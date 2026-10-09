package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsUpdateBody
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How long the tags wait after the last change before they save, so a burst of taps is one request. */
internal const val TAGS_SAVE_DELAY_MILLIS = 600L

/** The blocklisted tags page. */
sealed interface BlocklistTagsUiState {
    data object Loading : BlocklistTagsUiState

    /**
     * [tags] are the TMDB keyword ids as the user has left them, saved or about to be; [names] names them and the search
     * results. [saveFailed] is a save the server refused: the tags stay as they are, unsaved, until the next save.
     */
    data class Ready(
        val tags: List<Int>,
        val names: Map<Int, String> = emptyMap(),
        val search: KeywordSearch = KeywordSearch(),
        val saveFailed: Boolean = false,
    ) : BlocklistTagsUiState

    data class Error(
        val error: SeerrError,
    ) : BlocklistTagsUiState
}

/**
 * The automatic blocklist's tags, as a page of their own that saves as it changes (#930). It reads the tags from
 * `settings/main` and names them, searches TMDB's keywords as the user types, and writes each change a moment later.
 * `POST /settings/main` merges what it is sent, so a write carries the tags alone, under whichever name this server
 * keeps them (`blacklistedTags` before Seerr 3.0), and the rest of the General page is left as the server holds it.
 */
@HiltViewModel
class BlocklistTagsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val state = MutableStateFlow<BlocklistTagsUiState>(BlocklistTagsUiState.Loading)
        val uiState: StateFlow<BlocklistTagsUiState> = state.asStateFlow()

        private var blacklistNames = false
        private var search: Job? = null
        private var save: Job? = null

        /** A change that has not been sent yet: the page leaving before its delay is up must still send it. */
        private var unsent = false

        init {
            reload()
        }

        fun reload() {
            state.value = BlocklistTagsUiState.Loading
            viewModelScope.launch(dispatcher) {
                attempt { connection.api().mainSettings() }
                    .onSuccess { main ->
                        blacklistNames = main.usesBlacklistNames
                        val tags = main.tags.orEmpty().tagIds()
                        state.value = BlocklistTagsUiState.Ready(tags)
                        name(tags)
                    }.onFailure { state.value = BlocklistTagsUiState.Error(it.toSeerrError()) }
            }
        }

        /** Searches TMDB's keywords as the user types; a newer query cancels the one before, and blank clears the results. */
        fun search(query: String) {
            search?.cancel()
            if (query.isBlank()) {
                editReady { it.copy(search = KeywordSearch()) }
                return
            }
            search =
                viewModelScope.launch(dispatcher) {
                    delay(KEYWORD_SEARCH_DEBOUNCE_MILLIS)
                    editReady { it.copy(search = it.search.copy(searching = true, failed = false)) }
                    val found =
                        attempt { connection.api().searchKeywords(query.trim()).results }
                            .getOrNull()
                            ?.mapNotNull { dto -> dto.name?.let { Keyword(dto.id, it) } }
                    editReady {
                        it.copy(
                            // A keyword added from the results is named without another read.
                            names = it.names + found.orEmpty().associate { k -> k.id to k.name },
                            search = it.search.copy(results = found ?: it.search.results, searching = false, failed = found == null),
                        )
                    }
                }
        }

        /** Adds [id] to the tags or takes it out, and saves the tags a moment after the last change. */
        fun toggle(id: Int) {
            editReady { it.copy(tags = if (id in it.tags) it.tags - id else it.tags + id) }
            saveSoon(TAGS_SAVE_DELAY_MILLIS)
        }

        /** Sends the tags as they stand, after a save that failed: the failed changes and any made since, as one. */
        fun retry() = saveSoon(0)

        private fun saveSoon(wait: Long) {
            unsent = true
            save?.cancel()
            save =
                viewModelScope.launch(dispatcher) {
                    delay(wait)
                    val tags = (state.value as? BlocklistTagsUiState.Ready)?.tags ?: return@launch
                    unsent = false
                    // The write runs on the application's scope, so leaving the page cannot cancel it half-sent.
                    val saved = appScope.async(dispatcher) { write(tags) }.await()
                    editReady { it.copy(saveFailed = !saved) }
                }
        }

        private suspend fun write(tags: List<Int>): Boolean {
            val csv = tags.joinToString(",")
            val body =
                SeerrMainSettingsUpdateBody(
                    blocklistedTags = csv.takeUnless { blacklistNames },
                    blacklistedTags = csv.takeIf { blacklistNames },
                )
            return attempt { connection.api().updateMainSettings(body) }.isSuccess
        }

        /** Leaving the page with a change still waiting out its delay sends it now, on the application's scope. */
        override fun onCleared() {
            val tags = (state.value as? BlocklistTagsUiState.Ready)?.tags
            if (unsent && tags != null) {
                save?.cancel()
                appScope.launch(dispatcher) { write(tags) }
            }
        }

        private fun name(ids: List<Int>) {
            val missing = ids.filter { it !in ((state.value as? BlocklistTagsUiState.Ready)?.names ?: emptyMap()) }
            if (missing.isEmpty()) return
            viewModelScope.launch(dispatcher) {
                val api = connection.api()
                val named = missing.mapNotNull { id -> attempt { api.keyword(id) }.getOrNull()?.name?.let { id to it } }.toMap()
                editReady { it.copy(names = it.names + named) }
            }
        }

        private fun editReady(transform: (BlocklistTagsUiState.Ready) -> BlocklistTagsUiState.Ready) =
            state.update { current -> (current as? BlocklistTagsUiState.Ready)?.let(transform) ?: current }
    }

/** The server's comma-separated keyword ids. */
internal fun String.tagIds(): List<Int> = split(',').mapNotNull { it.trim().toIntOrNull() }
