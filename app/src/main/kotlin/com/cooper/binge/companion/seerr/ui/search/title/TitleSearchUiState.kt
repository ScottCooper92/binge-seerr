package com.cooper.binge.companion.seerr.ui.search.title

import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.seerr.SeerrError

/** The title search tab. [text] is what the field holds, which runs ahead of the query being searched. */
sealed interface TitleSearchUiState {
    val text: String

    /** Nothing searched yet, or too little typed: the queries searched before, newest first, and what is trending. */
    data class Idle(
        override val text: String,
        val recents: List<String>,
        val trending: Trending = Trending.Loading,
    ) : TitleSearchUiState

    data class Loading(
        override val text: String,
    ) : TitleSearchUiState

    data class Ready(
        override val text: String,
        val results: List<TitleResult>,
        /** How many results have loaded in all, whatever type is chosen: it grows with every page, which [results] may not. */
        val loaded: Int,
        val type: SearchType,
        val counts: Map<SearchType, Int>,
        val genreNames: Map<Int, String>,
        val hasMore: Boolean,
        val appending: Boolean,
        val appendFailed: Boolean,
    ) : TitleSearchUiState

    /**
     * The query found nothing. [type] is the chip still chosen, so the row of chips stays, each at zero. [browseAll] is whether
     * the server can filter the grid the Browse all row opens, as the hub asks.
     */
    data class NoResults(
        override val text: String,
        val query: String,
        val type: SearchType,
        val browseAll: Boolean = true,
    ) : TitleSearchUiState

    data class Error(
        override val text: String,
        val error: SeerrError,
    ) : TitleSearchUiState
}

/** The two trending rails under the recents. A failure leaves them out, as they are not what the user came for. */
sealed interface Trending {
    data object Loading : Trending

    data class Ready(
        val movies: List<TitleResult>,
        val series: List<TitleResult>,
    ) : Trending

    data object Unavailable : Trending
}
