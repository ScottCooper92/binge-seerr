package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.ListRefreshes
import io.github.scottcooper92.binge.seerr.data.RequestListQuery
import io.github.scottcooper92.binge.seerr.data.RequestStore
import io.github.scottcooper92.binge.seerr.data.RequestsRemoteMediator
import io.github.scottcooper92.binge.seerr.data.whileSet
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Who the list is scoped to: everyone's requests, or one user's where they may not see others'. */
private data class ListScope(
    val moderation: ModerationScope,
    val requestedBy: Int?,
)

/** Where resolving [ListScope] stands: in flight, done, or failed with why, so a failure is a state rather than a skeleton that never ends. */
private sealed interface ScopeState {
    data object Resolving : ScopeState

    data class Resolved(
        val scope: ListScope,
    ) : ScopeState

    data class Failed(
        val error: SeerrError,
    ) : ScopeState
}

/**
 * The requests browser. One cached paging stream per filter, each read from the cache and refreshed
 * through the mediator, so switching chips keeps each list's rows and a cold open shows the last
 * pages before the server answers; every stream re-queries when the sort changes. The chip counts and the resolved scope both
 * refetch on the screen becoming visible (the counts also on a filter change), holding the previous
 * value while a fetch is in flight. A failed `auth/me` read is an [RequestsUiState.Error] the user can
 * retry, and it also self-corrects the next time the screen becomes visible.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
@HiltViewModel
class RequestsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
        private val store: RequestStore,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    ) : ViewModel() {
        private val selectedFilter = MutableStateFlow(RequestFilter.All)
        private val selectedSort = MutableStateFlow(RequestSort.Added)
        private val refreshTrigger = MutableStateFlow(0)
        private val countsRefresh = MutableStateFlow(0)

        /** A `request/count` read is running, so a pull leaves it to finish rather than restarting it (#1193). */
        private val countsInFlight = AtomicBoolean(false)
        private val listVersionState = MutableStateFlow(0)

        /**
         * The version each filter's list last refreshed at: a filter refreshes once while it trails
         * [RequestsUiState.Ready.listVersion], so a page swiped away and back does not re-refresh a
         * current list.
         */
        private val refreshedVersions = ConcurrentHashMap<RequestFilter, Int>()

        private val moderator =
            RequestModeration(
                scope = viewModelScope,
                dispatcher = dispatcher,
                connection = connection,
                analytics = analytics,
                crashBreadcrumbs = crashBreadcrumbs,
                cache = store,
            ) { listChanged() }

        /** What the screen may ask of the moderation: its actions only. Its acting set is folded into [uiState] (#1048). */
        val moderation: RequestModerationControls = moderator

        /** How a moderation went, for the screen's snackbar. */
        val events: SharedFlow<ModerationEvent> = moderator.events

        /** A moderation finished, here or in a row's sheet: refetch the counts and stale the lists. */
        fun listChanged() {
            countsRefresh.value++
            listVersionState.update { it + 1 }
        }

        /** True at most once per version per filter, so a freshly composed, current page does not blank-refresh. */
        fun shouldRefresh(
            filter: RequestFilter,
            version: Int,
        ): Boolean {
            val last = refreshedVersions[filter] ?: 0
            if (version <= last) return false
            refreshedVersions[filter] = version
            return true
        }

        /**
         * The user's permissions decide whether the list is theirs alone, and what they may moderate.
         * Re-read from the server on becoming visible, since the cached `auth/me` would not show a
         * permission changed in the web client; a failed re-resolve is [ScopeState.Failed] rather than a
         * guessed, all-permissive scope, so nothing downstream acts on one. A retry from a failure
         * shows [ScopeState.Resolving] again. The profile is re-read too, so a server upgraded in place
         * offers its blocklist without a reconnect (#1074).
         *
         * `flowOn(dispatcher)` for the same reason [moderation] takes one (#177): without it, this
         * flow's own suspend calls resume on `viewModelScope`'s `Dispatchers.Main.immediate`, which
         * can outlive a cleared scope same as a plain `launch` would.
         */
        private val scope: StateFlow<ScopeState> =
            refreshTrigger
                .flatMapLatest {
                    flow {
                        if (scope.value is ScopeState.Failed) emit(ScopeState.Resolving)
                        emit(
                            attempt { connection.refreshAuthenticatedUser() }.fold(
                                onSuccess = { resolved ->
                                    val permissions = resolved.toPermissions()
                                    val hasBlocklist = attempt { connection.refreshProfile().hasBlocklist }.getOrDefault(false)
                                    ScopeState.Resolved(
                                        ListScope(
                                            moderation =
                                                ModerationScope(permissions, currentUserId = resolved.id, hasBlocklist = hasBlocklist),
                                            requestedBy = resolved.id.takeUnless { permissions.canViewRequests },
                                        ),
                                    )
                                },
                                onFailure = { ScopeState.Failed(it.toSeerrError()) },
                            ),
                        )
                    }
                }.flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.Lazily, ScopeState.Resolving)

        private val refreshes = ListRefreshes<RequestFilter>()

        private val streams: Map<RequestFilter, Flow<PagingData<RequestItem>>> =
            RequestFilter.entries.associateWith { filter ->
                combine(selectedSort, scope.filterIsInstance<ScopeState.Resolved>()) { sort, resolved -> sort to resolved.scope }
                    .flatMapLatest { (sort, scope) ->
                        val query = RequestListQuery(filter.apiValue, sort.apiValue, scope.requestedBy)
                        Pager(
                            config = PagingConfig(pageSize = REQUESTS_PAGE_SIZE),
                            remoteMediator =
                                RequestsRemoteMediator(
                                    query = query,
                                    api = connection::api,
                                    store = store,
                                    onRefresh = { rows -> refreshes.record(filter, rows) },
                                ) { dto, api, key, index ->
                                    dto.toRequestEntity(api, titles::get, key, index, System.currentTimeMillis())
                                },
                        ) { store.pagingSource(query.listKey) }.flow
                    }.map { data -> data.map { it.toRequestItem() } }
                    .cachedIn(viewModelScope)
            }

        fun requests(filter: RequestFilter): Flow<PagingData<RequestItem>> = streams.getValue(filter)

        /**
         * The chip counts, for a viewer who sees every request. `request/count` counts the whole server and has no
         * `requestedBy`, so for a viewer whose list is theirs alone it would count requests the list never shows (#977):
         * their chips carry no count, and it is not asked.
         */
        private val counts: Flow<RequestCounts?> =
            combine(selectedFilter, refreshTrigger, countsRefresh, scope) { _, _, _, scope ->
                scope is ScopeState.Resolved && scope.scope.requestedBy == null
            }.flatMapLatest { seesEveryRequest ->
                flow {
                    emit(
                        if (seesEveryRequest) {
                            countsInFlight.whileSet {
                                attempt { connection.api().requestCount() }
                                    .getOrNull()
                                    ?.let { RequestCounts(it.total, it.pending, it.approved, it.processing, it.available) }
                            }
                        } else {
                            null
                        },
                    )
                }
            }.flowOn(dispatcher)
                .onStart { emit(null) }

        val uiState: StateFlow<RequestsUiState> =
            combine(
                combine(selectedFilter, selectedSort, listVersionState) { filter, sort, version -> Triple(filter, sort, version) },
                counts,
                scope,
                moderator.actingIds,
                refreshes.latest,
            ) { (filter, sort, version), counts, scope, acting, refreshes ->
                when (scope) {
                    ScopeState.Resolving -> RequestsUiState.Loading
                    is ScopeState.Failed -> RequestsUiState.Error(scope.error)
                    is ScopeState.Resolved ->
                        RequestsUiState.Ready(
                            filter = filter,
                            sort = sort,
                            counts = counts,
                            scope = scope.scope.moderation,
                            actingIds = acting,
                            listVersion = version,
                            refreshes = refreshes,
                        )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, RequestsUiState.Loading)

        fun setFilter(filter: RequestFilter) {
            selectedFilter.value = filter
        }

        fun setSort(sort: RequestSort) {
            selectedSort.value = sort
        }

        /** Re-reads the signed-in user after [RequestsUiState.Error]. */
        fun retry() {
            refreshTrigger.value++
        }

        /** A pull refreshed the list: the chips re-read their counts too, unless a read is already running (#1193). */
        fun refreshCounts() {
            if (!countsInFlight.get()) countsRefresh.value++
        }

        /** The counts and the scope are low-velocity: refetched on entry, never polled. */
        fun setScreenVisible(visible: Boolean) {
            if (visible) refreshTrigger.value++
        }
    }
