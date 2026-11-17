package com.cooper.binge.companion.seerr.ui.search.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.core.search.PagedQuerySearch
import com.cooper.binge.companion.seerr.core.search.QueryHistory
import com.cooper.binge.companion.seerr.core.search.QuerySearchState
import com.cooper.binge.companion.seerr.core.search.SearchPage
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Title search: the generic [PagedQuerySearch] over the server's search. The tab root lives as long as the shell, so the state is
 * shared `Lazily`. Nothing here needs a refresh on return: the recents are a live flow, and a failure has its own Retry.
 */
@HiltViewModel
class TitleSearchViewModel
    @Inject
    constructor(
        titles: TitleListRepository,
        history: QueryHistory,
    ) : ViewModel() {
        private val search =
            PagedQuerySearch<TitleResult>(
                scope = viewModelScope,
                history = history,
                fetch = { query, page ->
                    val loaded = titles.load(TitleSource.Search(query), page)
                    SearchPage(loaded.results, loaded.page, loaded.totalPages)
                },
            )

        val uiState: StateFlow<TitleSearchUiState> =
            combine(search.query, search.state) { text, state -> state.toUiState(text) }
                .stateIn(viewModelScope, SharingStarted.Lazily, TitleSearchUiState.Idle("", emptyList()))

        fun onQueryChange(text: String) = search.setQuery(text)

        fun onClear() = search.clear()

        fun onSubmit() = search.submit()

        fun onRetry() = search.retry()

        fun onLoadMore() = search.loadMore()

        /** Search a recent query again: it fills the field and runs at once. */
        fun onRecent(query: String) {
            search.setQuery(query)
            search.submit()
        }

        fun onForgetRecents() {
            viewModelScope.launch { search.forgetRecents() }
        }
    }

internal fun QuerySearchState<TitleResult>.toUiState(text: String): TitleSearchUiState =
    when (this) {
        is QuerySearchState.Idle -> TitleSearchUiState.Idle(text, recents)
        is QuerySearchState.Loading -> TitleSearchUiState.Loading(text)
        is QuerySearchState.Results -> TitleSearchUiState.Ready(text, items, hasMore, appending, appendFailed)
        is QuerySearchState.NoResults -> TitleSearchUiState.NoResults(text, query)
        is QuerySearchState.Failed -> TitleSearchUiState.Error(text, cause.toSeerrError())
    }
