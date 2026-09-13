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
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The issues browser. One cached paging stream per filter, each read from the cache and refreshed
 * through the mediator, so switching chips keeps each list's rows and a cold open shows the last
 * pages before the server answers. The chip counts refetch on a filter change and on the screen
 * becoming visible, where the server has them.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
@HiltViewModel
class IssuesViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
        private val store: IssueStore,
    ) : ViewModel() {
        private val selectedFilter = MutableStateFlow(IssueFilter.Open)
        private val selectedSort = MutableStateFlow(IssueSort.Added)
        private val countsRefresh = MutableStateFlow(0)

        /** Resolved once per connection: the user's permissions decide whether the list is theirs alone. */
        private val scope: Flow<IssueListScope> =
            flow {
                val user = runCatching { connection.authenticatedUser() }.getOrNull()
                emit(IssueListScope(permissions = user.toPermissions(), currentUserId = user?.id))
            }.stateIn(viewModelScope, SharingStarted.Lazily, IssueListScope())

        private val streams: Map<IssueFilter, Flow<PagingData<IssueItem>>> =
            IssueFilter.entries.associateWith { filter ->
                combine(selectedSort, scope) { sort, scope -> IssueListQuery(filter.apiValue, sort.apiValue, scope.requestedBy) }
                    .flatMapLatest { query ->
                        Pager(
                            config = PagingConfig(pageSize = ISSUES_PAGE_SIZE),
                            remoteMediator =
                                IssuesRemoteMediator(query = query, api = connection::api, store = store) { dto, api, key, index ->
                                    dto.toIssueEntity(api, titles::get, key, index)
                                },
                        ) { store.pagingSource(query.listKey, filter.statusValue()) }.flow
                    }.map { data -> data.map { it.toIssueItem() } }
                    .cachedIn(viewModelScope)
            }

        fun issues(filter: IssueFilter): Flow<PagingData<IssueItem>> = streams.getValue(filter)

        /** Null where the server has no counts endpoint, or it failed; the previous totals hold while a fetch is in flight. */
        private val counts: Flow<IssueCounts?> =
            combine(selectedFilter, countsRefresh) { _, _ -> }
                .flatMapLatest {
                    flow {
                        val profile = runCatching { connection.profile() }.getOrNull()
                        emit(
                            if (profile?.hasCounts == true) {
                                runCatching { connection.api().issueCount() }.getOrNull()?.let { IssueCounts(it.total, it.open, it.closed) }
                            } else {
                                null
                            },
                        )
                    }
                }.onStart { emit(null) }

        val uiState: StateFlow<IssuesUiState> =
            combine(selectedFilter, selectedSort, counts, scope) { filter, sort, counts, scope ->
                IssuesUiState.Ready(filter = filter, sort = sort, counts = counts, scope = scope)
            }.stateIn(viewModelScope, SharingStarted.Lazily, IssuesUiState.Loading)

        fun setFilter(filter: IssueFilter) {
            selectedFilter.value = filter
        }

        fun setSort(sort: IssueSort) {
            selectedSort.value = sort
        }

        /** The counts are low-velocity totals: refetched on entry, never polled. */
        fun setScreenVisible(visible: Boolean) {
            if (visible) countsRefresh.value++
        }
    }
