package io.github.scottcooper92.binge.seerr.ui.issues

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
import io.github.scottcooper92.binge.seerr.data.ISSUES_PAGE_SIZE
import io.github.scottcooper92.binge.seerr.data.IssueListQuery
import io.github.scottcooper92.binge.seerr.data.IssueStore
import io.github.scottcooper92.binge.seerr.data.IssuesRemoteMediator
import io.github.scottcooper92.binge.seerr.data.ListRefreshes
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    ) : ViewModel() {
        private val selectedFilter = MutableStateFlow(IssueFilter.Open)
        private val selectedSort = MutableStateFlow(IssueSort.Added)
        private val countsRefresh = MutableStateFlow(0)
        private val scopeRefresh = MutableStateFlow(0)
        private val actionItem = MutableStateFlow<IssueItem?>(null)
        private val actingState = MutableStateFlow<Set<Int>>(emptySet())
        private val eventFlow = MutableSharedFlow<IssueListEvent>(extraBufferCapacity = 1)

        /** The outcome of each row action, once. */
        val events: Flow<IssueListEvent> = eventFlow.asSharedFlow()

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
                                attempt { connection.api().issueCount() }.getOrNull()?.let { IssueCounts(it.total, it.open, it.closed) }
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
                combine(actingState, actionItem) { acting, actionItem -> acting to actionItem },
                refreshes.latest,
            ) { (filter, sort), counts, scope, (acting, actionItem), refreshes ->
                when (scope) {
                    ScopeState.Resolving -> IssuesUiState.Loading
                    is ScopeState.Failed -> IssuesUiState.Error(scope.error)
                    is ScopeState.Resolved ->
                        IssuesUiState.Ready(
                            filter = filter,
                            sort = sort,
                            counts = counts,
                            scope = scope.scope,
                            actingIds = acting,
                            actionItem = actionItem,
                            refreshes = refreshes,
                        )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, IssuesUiState.Loading)

        fun openActions(item: IssueItem) {
            actionItem.value = item
        }

        fun dismissActions() {
            actionItem.value = null
        }

        /** Marks an open issue resolved; the cached row moves with it, so the list agrees at once. */
        fun resolve(item: IssueItem) =
            act(item, IssueListEvent.Resolved, "resolved") {
                connection.api().setIssueStatus(item.id, STATUS_RESOLVED)
                store.updateStatus(item.id, IssueStatus.Resolved.name)
            }

        fun reopen(item: IssueItem) =
            act(item, IssueListEvent.Reopened, "reopened") {
                connection.api().setIssueStatus(item.id, STATUS_OPEN)
                store.updateStatus(item.id, IssueStatus.Open.name)
            }

        /** Removes the report and its whole thread from the server and the cache. */
        fun delete(item: IssueItem) =
            act(item, IssueListEvent.Deleted, "deleted") {
                connection.api().deleteIssue(item.id)
                store.delete(item.id)
            }

        private fun act(
            item: IssueItem,
            success: IssueListEvent,
            action: String,
            write: suspend () -> Unit,
        ) {
            if (item.id in actingState.value) return
            actingState.update { it + item.id }
            crashBreadcrumbs.key("issue_id", item.id.toString())
            crashBreadcrumbs.log("$action issue")
            viewModelScope.launch(dispatcher) {
                val result = attempt { write() }
                actingState.update { it - item.id }
                result
                    .onSuccess {
                        countsRefresh.value++
                        analytics.event(AnalyticsEvents.ISSUE_MODERATED, mapOf(AnalyticsEvents.PARAM_ACTION to action))
                        eventFlow.emit(success)
                    }.onFailure { failure -> eventFlow.emit(IssueListEvent.Failed(failure.toSeerrError())) }
            }
        }

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

        /** The counts and the viewer's permissions are both refetched on entry, and neither is polled. */
        fun setScreenVisible(visible: Boolean) {
            if (!visible) return
            countsRefresh.value++
            scopeRefresh.value++
        }
    }
