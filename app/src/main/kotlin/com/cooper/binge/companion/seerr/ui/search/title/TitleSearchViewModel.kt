package com.cooper.binge.companion.seerr.ui.search.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.discover.StudioResult
import com.cooper.binge.companion.seerr.discover.StudioSearch
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.overlaid
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.homelab.core.presentation.paging.numberedPage
import com.cooper.homelab.core.presentation.search.PagedQuerySearch
import com.cooper.homelab.core.presentation.search.QueryHistory
import com.cooper.homelab.core.presentation.search.QuerySearchState
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
        private val grid: DiscoverGridRepository,
        statuses: TitleStatusStore = TitleStatusStore(),
    ) : ViewModel() {
        private val eventFlow = MutableSharedFlow<TitleSearchEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<TitleSearchEvent> = eventFlow.asSharedFlow()

        private val search =
            PagedQuerySearch<TitleResult, Int>(
                scope = viewModelScope,
                history = history,
                first = FIRST_PAGE,
                fetch = { query, page ->
                    val loaded = titles.load(TitleSource.Search(query), page)
                    numberedPage(loaded.results, loaded.page, loaded.totalPages)
                },
                classify = { it.toServerFailure() },
                keyOf = { it.kind to it.tmdbId },
            )

        /** The studios for the same query, kept apart from the titles: its own page and its own failure, and no history of its own. */
        private val studioSearch =
            PagedQuerySearch<StudioResult, Int>(
                scope = viewModelScope,
                history = NoQueryHistory,
                first = FIRST_PAGE,
                fetch = { query, page -> studios.search(query, page) },
                classify = { it.toServerFailure() },
                keyOf = { it.id },
            )

        private val type = MutableStateFlow(SearchType.All)
        private val trending = MutableStateFlow<Trending>(Trending.Loading)
        private val genreNames = MutableStateFlow<Map<Int, String>>(emptyMap())
        private val browseAll = MutableStateFlow(true)
        private val hasStudios = MutableStateFlow(true)
        private val lookups = combine(genreNames, browseAll, hasStudios) { genres, browse, studios -> Lookups(genres, browse, studios) }

        /** What surrounds the results: the trending rails with the genre names and the Browse all answer, which share one flow. */
        private val around = combine(trending, lookups) { trending, lookups -> trending to lookups }

        val uiState: StateFlow<TitleSearchUiState> =
            combine(
                search.query,
                search.state.combine(statuses.statuses) { state, held -> state.overlaid(held) },
                studioSearch.state,
                type,
                around,
            ) { text, state, studios, type, (trending, lookups) ->
                state.toUiState(text, type, trending, lookups.genres, lookups.browseAll, studios, lookups.studios)
            }.stateIn(viewModelScope, SharingStarted.Lazily, TitleSearchUiState.Idle("", emptyList()))

        init {
            loadTrending()
            viewModelScope.launch { hasStudios.value = attempt { studios.supported() }.getOrDefault(true) }
            viewModelScope.launch { browseAll.value = attempt { grid.canRefine(GridPreset.Popular) }.getOrDefault(true) }
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

        /** On Studios, a page that failed is asked for again, as a failed list has no next page to load. */
        fun onLoadMore() {
            when {
                type.value != SearchType.Studios -> search.loadMore()
                studioSearch.state.value is QuerySearchState.Failed -> studioSearch.retry()
                else -> studioSearch.loadMore()
            }
        }

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
    browseAll: Boolean = true,
    studios: QuerySearchState<StudioResult> = QuerySearchState.Idle(emptyList()),
    hasStudios: Boolean = true,
): TitleSearchUiState {
    val found = studios as? QuerySearchState.Results
    val studioRows = found?.items.orEmpty()
    // On Studios the footer, the retry and the spinner are the studios' own, whatever state their search is in.
    val onStudios = type == SearchType.Studios

    fun ready(
        all: List<TitleResult>,
        titleMore: QuerySearchState.Results<TitleResult>?,
    ) = TitleSearchUiState.Ready(
        text = text,
        results = all.filter(type::keeps),
        studios = studioRows,
        loaded = all.size,
        type = type,
        counts = all.typeCounts(studioRows.size),
        genreNames = genreNames,
        hasMore = if (onStudios) found?.hasMore ?: false else titleMore?.hasMore ?: false,
        appending = if (onStudios) found?.appending ?: (studios is QuerySearchState.Loading) else titleMore?.appending ?: false,
        appendFailed = if (onStudios) found?.appendFailed ?: (studios is QuerySearchState.Failed) else titleMore?.appendFailed ?: false,
        hasStudios = hasStudios,
    )
    return when (this) {
        is QuerySearchState.Idle -> TitleSearchUiState.Idle(text, recents, trending)
        is QuerySearchState.Loading -> TitleSearchUiState.Loading(text)
        is QuerySearchState.Results -> ready(items.distinctBy(TitleResult::key), this)
        is QuerySearchState.NoResults ->
            if (found != null) ready(emptyList(), null) else TitleSearchUiState.NoResults(text, query, type, browseAll, hasStudios)
        is QuerySearchState.Failed -> TitleSearchUiState.Error(text, failure.toSeerrError())
    }
}

/** What the Search tab asks its host to do. */
sealed interface TitleSearchEvent {
    data class Open(
        val card: HubCard,
    ) : TitleSearchEvent
}

/** What the lists around the results are made from: the genre names, the Browse all answer and whether the server has company search. */
private data class Lookups(
    val genres: Map<Int, String>,
    val browseAll: Boolean,
    val studios: Boolean,
)

/** Seerr counts its pages from one. */
private const val FIRST_PAGE = 1
