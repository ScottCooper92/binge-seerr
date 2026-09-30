package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
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
 * The requests browser. One cached paging stream per filter, so switching chips keeps each list's
 * rows; every stream re-queries when the sort changes. The chip counts and the resolved scope both
 * refetch on the screen becoming visible (the counts also on a filter change), holding the previous
 * value while a fetch is in flight. A failed `auth/me` read is an [RequestsUiState.Error] the user can
 * retry, and it also self-corrects the next time the screen becomes visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RequestsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    ) : ViewModel() {
        private val selectedFilter = MutableStateFlow(RequestFilter.All)
        private val selectedSort = MutableStateFlow(RequestSort.Added)
        private val refreshTrigger = MutableStateFlow(0)
        private val countsRefresh = MutableStateFlow(0)
        private val actionItem = MutableStateFlow<RequestItem?>(null)
        private val listVersionState = MutableStateFlow(0)

        /**
         * The version each filter's list last refreshed at: a filter refreshes once while it trails
         * [RequestsUiState.Ready.listVersion], so a page swiped away and back does not re-refresh a
         * current list.
         */
        private val refreshedVersions = ConcurrentHashMap<RequestFilter, Int>()

        val moderation =
            RequestModeration(
                scope = viewModelScope,
                dispatcher = dispatcher,
                connection = connection,
                analytics = analytics,
                crashBreadcrumbs = crashBreadcrumbs,
            ) {
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
         * shows [ScopeState.Resolving] again. The profile is not re-read: `hasBlocklist` follows the
         * server's version, which an upgrade restarts anyway.
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
                            runCatching { connection.refreshAuthenticatedUser() }.fold(
                                onSuccess = { resolved ->
                                    val permissions = resolved.toPermissions()
                                    val hasBlocklist = runCatching { connection.profile().hasBlocklist }.getOrDefault(false)
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

        private val streams: Map<RequestFilter, Flow<PagingData<RequestItem>>> =
            RequestFilter.entries.associateWith { filter ->
                combine(selectedSort, scope.filterIsInstance<ScopeState.Resolved>()) { sort, resolved -> sort to resolved.scope }
                    .flatMapLatest { (sort, scope) ->
                        Pager(PagingConfig(pageSize = REQUESTS_PAGE_SIZE)) {
                            RequestsPagingSource(
                                api = connection::api,
                                filter = filter,
                                sort = sort,
                                requestedBy = scope.requestedBy,
                                hydrate = titles::get,
                            )
                        }.flow
                    }.cachedIn(viewModelScope)
            }

        fun requests(filter: RequestFilter): Flow<PagingData<RequestItem>> = streams.getValue(filter)

        private val counts: Flow<RequestCounts?> =
            combine(selectedFilter, refreshTrigger, countsRefresh) { _, _, _ -> }
                .flatMapLatest {
                    flow {
                        emit(
                            runCatching { connection.api().requestCount() }
                                .getOrNull()
                                ?.let { RequestCounts(it.total, it.pending, it.approved, it.processing, it.available) },
                        )
                    }
                }.flowOn(dispatcher)
                .onStart { emit(null) }

        val uiState: StateFlow<RequestsUiState> =
            combine(
                combine(selectedFilter, selectedSort, listVersionState) { filter, sort, version -> Triple(filter, sort, version) },
                counts,
                scope,
                moderation.actingIds,
                actionItem,
            ) { (filter, sort, version), counts, scope, acting, actionItem ->
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
                            actionItem = actionItem,
                        )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, RequestsUiState.Loading)

        fun openActions(item: RequestItem) {
            actionItem.value = item
        }

        fun dismissActions() {
            actionItem.value = null
        }

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

        /** The counts and the scope are low-velocity: refetched on entry, never polled. */
        fun setScreenVisible(visible: Boolean) {
            if (visible) refreshTrigger.value++
        }
    }
