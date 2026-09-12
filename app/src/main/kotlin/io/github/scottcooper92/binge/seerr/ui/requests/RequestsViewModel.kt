package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Who the list is scoped to: everyone's requests, or one user's where they may not see others'. */
private data class ListScope(
    val permissions: SeerrPermissions,
    val requestedBy: Int?,
)

/**
 * The requests browser. One cached paging stream per filter, so switching chips keeps each list's
 * rows; every stream re-queries when the sort changes. The chip counts refetch on a filter change
 * and on the screen becoming visible, holding the previous totals while a fetch is in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RequestsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
    ) : ViewModel() {
        private val selectedFilter = MutableStateFlow(RequestFilter.All)
        private val selectedSort = MutableStateFlow(RequestSort.Added)
        private val countsRefresh = MutableStateFlow(0)

        /** Resolved once per connection: the user's permissions decide whether the list is theirs alone. */
        private val scope: Flow<ListScope> =
            flow {
                val user = runCatching { connection.authenticatedUser() }.getOrNull()
                val permissions = user.toPermissions()
                emit(ListScope(permissions, requestedBy = user?.id?.takeUnless { permissions.canViewRequests }))
            }.stateIn(viewModelScope, SharingStarted.Lazily, ListScope(SeerrPermissions(), requestedBy = null))

        private val streams: Map<RequestFilter, Flow<PagingData<RequestItem>>> =
            RequestFilter.entries.associateWith { filter ->
                combine(selectedSort, scope) { sort, scope -> sort to scope }
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
            combine(selectedFilter, countsRefresh) { _, _ -> }
                .flatMapLatest {
                    flow {
                        emit(
                            runCatching { connection.api().requestCount() }
                                .getOrNull()
                                ?.let { RequestCounts(it.total, it.pending, it.approved, it.processing, it.available) },
                        )
                    }
                }.onStart { emit(null) }

        val uiState: StateFlow<RequestsUiState> =
            combine(selectedFilter, selectedSort, counts, scope) { filter, sort, counts, scope ->
                RequestsUiState.Ready(filter = filter, sort = sort, counts = counts, permissions = scope.permissions)
            }.stateIn(viewModelScope, SharingStarted.Lazily, RequestsUiState.Loading)

        fun setFilter(filter: RequestFilter) {
            selectedFilter.value = filter
        }

        fun setSort(sort: RequestSort) {
            selectedSort.value = sort
        }

        /** The counts are low-velocity totals: refetched on entry, never polled. */
        fun setScreenVisible(visible: Boolean) {
            if (visible) countsRefresh.value++
        }
    }
