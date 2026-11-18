package com.cooper.binge.companion.seerr.ui.search.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.core.search.PagedQuerySearch
import com.cooper.binge.companion.seerr.core.search.QueryHistory
import com.cooper.binge.companion.seerr.core.search.QuerySearchState
import com.cooper.binge.companion.seerr.core.search.SearchPage
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        private val titles: TitleListRepository,
        history: QueryHistory,
        private val media: HubMediaSource,
    ) : ViewModel() {
        private val eventFlow = MutableSharedFlow<TitleSearchEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<TitleSearchEvent> = eventFlow.asSharedFlow()

        private val search =
            PagedQuerySearch<TitleResult>(
                scope = viewModelScope,
                history = history,
                fetch = { query, page ->
                    val loaded = titles.load(TitleSource.Search(query), page)
                    SearchPage(loaded.results, loaded.page, loaded.totalPages, loaded.total)
                },
                keyOf = { it.kind to it.tmdbId },
            )

        private val type = MutableStateFlow(SearchType.All)
        private val trending = MutableStateFlow<Trending>(Trending.Loading)
        private val genreNames = MutableStateFlow<Map<Int, String>>(emptyMap())

        val uiState: StateFlow<TitleSearchUiState> =
            combine(search.query, search.state, type, trending, genreNames) { text, state, type, trending, genres ->
                state.toUiState(text, type, trending, genres)
            }.stateIn(viewModelScope, SharingStarted.Lazily, TitleSearchUiState.Idle("", emptyList()))

        init {
            loadTrending()
            viewModelScope.launch { genreNames.value = attempt { titles.allGenreNames() }.getOrDefault(emptyMap()) }
        }

        private fun loadTrending() {
            viewModelScope.launch {
                trending.value = attempt { titles.trendingRails() }.getOrNull() ?: Trending.Unavailable
            }
        }

        fun onType(selected: SearchType) {
            type.value = selected
        }

        /** A new query starts on All again, as a type chosen for the last one may not be among this one's results. */
        fun onQueryChange(text: String) {
            if (text.trim() != search.query.value.trim()) type.value = SearchType.All
            search.setQuery(text)
        }

        fun onClear() {
            type.value = SearchType.All
            search.clear()
            if (trending.value is Trending.Unavailable) {
                trending.value = Trending.Loading
                loadTrending()
            }
        }

        fun onSubmit() = search.submit()

        fun onRetry() = search.retry()

        fun onLoadMore() = search.loadMore()

        /** Search a recent query again: it fills the field and runs at once. */
        fun onRecent(query: String) {
            type.value = SearchType.All
            search.setQuery(query)
            search.submit()
        }

        /** A movie, a series or a person opens; the card carries the server's page for it, where Binge is not there to take it. */
        fun onOpenTitle(result: TitleResult) {
            viewModelScope.launch { eventFlow.emit(TitleSearchEvent.Open(result.toCard(media.serverUrl()))) }
        }

        fun onForgetRecents() {
            viewModelScope.launch { search.forgetRecents() }
        }
    }

private fun TitleResult.key() = kind to tmdbId

/** A movie's genres and a series' come from two lists whose ids do not clash, so one map names both. */
private suspend fun TitleListRepository.allGenreNames(): Map<Int, String> = genreNames(TitleKind.Movie) + genreNames(TitleKind.Series)

/** The server's trending list holds movies, series and people; the rails are the first two, from its first page. */
private suspend fun TitleListRepository.trendingRails(): Trending {
    val page = load(TitleSource.Trending, 1)
    return Trending.Ready(
        movies = page.results.filter { it.kind == TitleKind.Movie },
        series = page.results.filter { it.kind == TitleKind.Series },
    )
}

/** Rows are keyed by kind and TMDB id, and a title can come back on two pages, so a repeat is dropped. */
internal fun QuerySearchState<TitleResult>.toUiState(
    text: String,
    type: SearchType,
    trending: Trending,
    genreNames: Map<Int, String>,
): TitleSearchUiState =
    when (this) {
        is QuerySearchState.Idle -> TitleSearchUiState.Idle(text, recents, trending)
        is QuerySearchState.Loading -> TitleSearchUiState.Loading(text)
        is QuerySearchState.Results -> {
            val all = items.distinctBy(TitleResult::key)
            TitleSearchUiState.Ready(
                text = text,
                results = all.filter(type::keeps),
                loaded = all.size,
                type = type,
                counts = all.typeCounts(total),
                genreNames = genreNames,
                hasMore = hasMore,
                appending = appending,
                appendFailed = appendFailed,
            )
        }
        is QuerySearchState.NoResults -> TitleSearchUiState.NoResults(text, query)
        is QuerySearchState.Failed -> TitleSearchUiState.Error(text, cause.toSeerrError())
    }

/** What the Search tab asks its host to do. */
sealed interface TitleSearchEvent {
    data class Open(
        val card: HubCard,
    ) : TitleSearchEvent
}
