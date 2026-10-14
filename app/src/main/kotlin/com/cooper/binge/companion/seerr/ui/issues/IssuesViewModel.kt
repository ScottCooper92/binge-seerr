package com.cooper.binge.companion.seerr.ui.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.binge.companion.sdk.attempt
import com.binge.companion.sdk.paging.ListRefreshes
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.ISSUES_PAGE_SIZE
import com.cooper.binge.companion.seerr.data.IssueListQuery
import com.cooper.binge.companion.seerr.data.IssueStore
import com.cooper.binge.companion.seerr.data.IssuesRemoteMediator
import com.cooper.binge.companion.seerr.data.whileSet
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.Ticker
import com.cooper.binge.companion.seerr.ui.minuteClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Where resolving the [IssueListScope] stands: in flight, done, or failed with why, so a failure is a state rather than an empty scope. */
private sealed interface ScopeState {
    data object Resolving : ScopeState

    data class Resolved(
        val scope: IssueListScope,
    ) : ScopeState

    data class Failed(
        val error: SeerrError,
    ) : ScopeState
}

/**
 * The issues browser. One cached paging stream per filter, each read from the cache and refreshed
 * through the mediator, so switching chips keeps each list's rows and a cold open shows the last
 * pages before the server answers. The chip counts refetch on a filter change and on the screen
 * becoming visible, where the server has them. A failed `auth/me` read is an [IssuesUiState.Error] the user can
 * retry, and it also self-corrects the next time the screen becomes visible.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
@HiltViewModel
class IssuesViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
        private val store: IssueStore,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val minuteTicker: Ticker = Ticker(),
    ) : ViewModel() {
        internal var clock: () -> Long = System::currentTimeMillis

        /** The rows' "2 minutes ago" is worded against this, moved on each minute while the list shows (#1239). */
        private val now = MutableStateFlow(clock())
        private var ticker: Job? = null

        private val selectedFilter = MutableStateFlow(IssueFilter.Open)
        private val selectedSort = MutableStateFlow(IssueSort.Added)
        private val countsRefresh = MutableStateFlow(0)

        /** An `issue/count` read is running, so a pull leaves it to finish rather than restarting it (#1193). */
        private val countsInFlight = AtomicBoolean(false)
        private val scopeRefresh = MutableStateFlow(0)

        /**
         * The user's permissions decide whether the list is theirs alone, and the server's version whether it has
         * counts. Both are re-read from the server on becoming visible, since the cached `auth/me` would not show a
         * permission changed in the web client, and the cached profile would not show a server upgraded in place (#1074).
         * A failed `auth/me` is [ScopeState.Failed] rather than a scope with no permissions, so nothing downstream acts
         * on one (#1073). A retry from a failure shows [ScopeState.Resolving] again. The profile stays best-effort: a
         * failed read only hides the counts.
         */
        private val scope: StateFlow<ScopeState> =
            scopeRefresh
                .flatMapLatest {
                    flow {
                        if (scope.value is ScopeState.Failed) emit(ScopeState.Resolving)
                        emit(
                            attempt { connection.refreshAuthenticatedUser() }.fold(
                                onSuccess = { user ->
                                    val hasCounts = attempt { connection.refreshProfile().hasCounts }.getOrDefault(false)
                                    ScopeState.Resolved(
                                        IssueListScope(permissions = user.toPermissions(), currentUserId = user.id, hasCounts = hasCounts),
                                    )
                                },
                                onFailure = { ScopeState.Failed(it.toSeerrError()) },
                            ),
                        )
                    }
                }.flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.Lazily, ScopeState.Resolving)

        private val refreshes = ListRefreshes<IssueFilter>()

        private val streams: Map<IssueFilter, Flow<PagingData<IssueItem>>> =
            IssueFilter.entries.associateWith { filter ->
                combine(selectedSort, scope.filterIsInstance<ScopeState.Resolved>()) { sort, resolved ->
                    IssueListQuery(filter.apiValue, sort.apiValue, resolved.scope.createdBy)
                }.flatMapLatest { query ->
                    Pager(
                        config = PagingConfig(pageSize = ISSUES_PAGE_SIZE),
                        remoteMediator =
                            IssuesRemoteMediator(
                                query = query,
                                api = connection::api,
                                store = store,
                                onRefresh = { rows -> refreshes.record(filter, rows) },
                            ) { dto, api, key, index ->
                                dto.toIssueEntity(api, titles::get, key, index)
                            },
                    ) { store.pagingSource(query.listKey, filter.statusValue()) }.flow
                }.map { data -> data.map { it.toIssueItem() } }
                    .cachedIn(viewModelScope)
            }

        fun issues(filter: IssueFilter): Flow<PagingData<IssueItem>> = streams.getValue(filter)

        /** Null where the server has no counts endpoint, or it failed; the previous totals hold while a fetch is in flight. */
        private val counts: Flow<IssueCounts?> =
            combine(
                selectedFilter,
                countsRefresh,
                scope.map { (it as? ScopeState.Resolved)?.scope?.hasCounts == true }.distinctUntilChanged(),
            ) { _, _, hasCounts -> hasCounts }
                .flatMapLatest { hasCounts ->
                    flow {
                        emit(
                            if (hasCounts) {
                                countsInFlight.whileSet {
                                    attempt { connection.api().issueCount() }.getOrNull()?.let { IssueCounts(it.total, it.open, it.closed) }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }.flowOn(dispatcher)
                .onStart { emit(null) }

        val uiState: StateFlow<IssuesUiState> =
            combine(
                combine(selectedFilter, selectedSort) { filter, sort -> filter to sort },
                counts,
                scope,
                refreshes.latest,
            ) { (filter, sort), counts, scope, refreshes ->
                when (scope) {
                    ScopeState.Resolving -> IssuesUiState.Loading
                    is ScopeState.Failed -> IssuesUiState.Error(scope.error)
                    is ScopeState.Resolved ->
                        IssuesUiState.Ready(
                            filter = filter,
                            sort = sort,
                            counts = counts,
                            scope = scope.scope,
                            refreshes = refreshes,
                        )
                }
            }.combine(now) { state, now -> (state as? IssuesUiState.Ready)?.copy(now = now) ?: state }
                .stateIn(viewModelScope, SharingStarted.Lazily, IssuesUiState.Loading)

        fun setFilter(filter: IssueFilter) {
            selectedFilter.value = filter
        }

        fun setSort(sort: IssueSort) {
            selectedSort.value = sort
        }

        /** Re-reads the signed-in user after [IssuesUiState.Error]. */
        fun retry() {
            scopeRefresh.value++
        }

        /**
         * A pull refreshed the list, or an issue changed on its own page: the chips re-read their counts too, unless a
         * read is already running (#1193).
         */
        fun refreshCounts() {
            if (!countsInFlight.get()) countsRefresh.value++
        }

        /** The counts and the viewer's permissions are both refetched on entry, and neither is polled. The rows' clock runs only while shown. */
        fun setScreenVisible(visible: Boolean) {
            ticker?.cancel()
            ticker = null
            if (!visible) return
            countsRefresh.value++
            scopeRefresh.value++
            ticker = viewModelScope.launch(dispatcher) { minuteClock(clock, minuteTicker).collect { now.value = it } }
        }
    }
