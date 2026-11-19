package com.cooper.binge.companion.seerr.ui.search.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.core.search.PagedQuerySearch
import com.cooper.binge.companion.seerr.core.search.QueryHistory
import com.cooper.binge.companion.seerr.core.search.QuerySearchState
import com.cooper.binge.companion.seerr.core.search.SearchPage
import com.cooper.binge.companion.seerr.discover.StudioResult
import com.cooper.binge.companion.seerr.discover.StudioSearch
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
        studios: StudioSearch,
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

        /** The studios for the same query, kept apart from the titles: its own page and its own failure, and no history of its own. */
        private val studioSearch =
            PagedQuerySearch<StudioResult>(
                scope = viewModelScope,
                history = NoQueryHistory,
                fetch = { query, page -> studios.search(query, page) },
                keyOf = { it.id },
            )

        private val type = MutableStateFlow(SearchType.All)
        private val trending = MutableStateFlow<Trending>(Trending.Loading)
        private val genreNames = MutableStateFlow<Map<Int, String>>(emptyMap())

        /** What the lists around the results come from: the trending rails and the genre names, which share one flow. */
        private val around = combine(trending, genreNames) { trending, genres -> trending to genres }

        val uiState: StateFlow<TitleSearchUiState> =
            combine(search.query, search.state, studioSearch.state, type, around) { text, state, studios, type, (trending, genres) ->
                state.toUiState(text, type, trending, genres, studios)
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
            studioSearch.setQuery(text)
        }

        fun onClear() {
            type.value = SearchType.All
            search.clear()
            studioSearch.clear()
            if (trending.value is Trending.Unavailable) {
                trending.value = Trending.Loading
                loadTrending()
            }
        }

        fun onSubmit() {
            search.submit()
            studioSearch.submit()
        }

        fun onRetry() {
            search.retry()
            studioSearch.retry()
        }

        fun onLoadMore() = if (type.value == SearchType.Studios) studioSearch.loadMore() else search.loadMore()

        /** Search a recent query again: it fills the field and runs at once. */
        fun onRecent(query: String) {
            type.value = SearchType.All
            search.setQuery(query)
            studioSearch.setQuery(query)
            search.submit()
            studioSearch.submit()
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

/** A movie's genres and a series' come from two lists that share a name where they share an id, so one map names both. */
private suspend fun TitleListRepository.allGenreNames(): Map<Int, String> = genreNames(TitleKind.Movie) + genreNames(TitleKind.Series)

/** The server's trending list holds movies, series and people; the rails are the first two, from its first page. */
private suspend fun TitleListRepository.trendingRails(): Trending {
    if (!supports(TitleSource.Trending)) return Trending.Unavailable
    val page = load(TitleSource.Trending, 1)
    return Trending.Ready(
        movies = page.results.filter { it.kind == TitleKind.Movie },
        series = page.results.filter { it.kind == TitleKind.Series },
    )
}

/** Nothing to remember: the studios search runs beside the titles', whose query the history already holds. */
private object NoQueryHistory : QueryHistory {
    override val recents: Flow<List<String>> = flowOf(emptyList())

    override suspend fun remember(query: String) = Unit

    override suspend fun clear() = Unit
}

/**
 * Rows are keyed by kind and TMDB id, and a title can come back on two pages, so a repeat is dropped. A query the titles do not
 * answer still shows the chips when the studios do, with the Studios chip to open.
 */
internal fun QuerySearchState<TitleResult>.toUiState(
    text: String,
    type: SearchType,
    trending: Trending,
    genreNames: Map<Int, String>,
    studios: QuerySearchState<StudioResult> = QuerySearchState.Idle(emptyList()),
): TitleSearchUiState {
    val found = studios as? QuerySearchState.Results
    val studioRows = found?.items.orEmpty()
    // The loading and failure shown are the chosen list's: on Studios, the studios'.
    val onStudios = type == SearchType.Studios && found != null

    fun ready(
        all: List<TitleResult>,
        more: QuerySearchState.Results<TitleResult>?,
    ) = TitleSearchUiState.Ready(
        text = text,
        results = all.filter(type::keeps),
        studios = studioRows,
        loaded = all.size,
        type = type,
        counts = all.typeCounts(studioRows.size),
        genreNames = genreNames,
        hasMore = if (onStudios) found.hasMore else more?.hasMore ?: false,
        appending = if (onStudios) found.appending else more?.appending ?: false,
        appendFailed = if (onStudios) found.appendFailed else more?.appendFailed ?: false,
    )
    return when (this) {
        is QuerySearchState.Idle -> TitleSearchUiState.Idle(text, recents, trending)
        is QuerySearchState.Loading -> TitleSearchUiState.Loading(text)
        is QuerySearchState.Results -> ready(items.distinctBy(TitleResult::key), this)
        is QuerySearchState.NoResults -> if (found != null) ready(emptyList(), null) else TitleSearchUiState.NoResults(text, query, type)
        is QuerySearchState.Failed -> TitleSearchUiState.Error(text, cause.toSeerrError())
    }
}

/** What the Search tab asks its host to do. */
sealed interface TitleSearchEvent {
    data class Open(
        val card: HubCard,
    ) : TitleSearchEvent
}
