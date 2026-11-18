package com.cooper.binge.companion.seerr.ui.search.title

import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.seerr.SeerrError

/** The title search tab. [text] is what the field holds, which runs ahead of the query being searched. */
sealed interface TitleSearchUiState {
    val text: String

    /** Nothing searched yet, or too little typed: the queries searched before, newest first. */
    data class Idle(
        override val text: String,
        val recents: List<String>,
    ) : TitleSearchUiState

    data class Loading(
        override val text: String,
    ) : TitleSearchUiState

    data class Ready(
        override val text: String,
        val results: List<TitleResult>,
        val hasMore: Boolean,
        val appending: Boolean,
        val appendFailed: Boolean,
    ) : TitleSearchUiState

    data class NoResults(
        override val text: String,
        val query: String,
    ) : TitleSearchUiState

    data class Error(
        override val text: String,
        val error: SeerrError,
    ) : TitleSearchUiState
}
