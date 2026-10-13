package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.save.SaveAsMade
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrMainSettingsUpdateBody
import com.cooper.binge.companion.seerr.seerr.attempt
import com.cooper.binge.companion.seerr.seerr.isTransient
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The blocklisted tags page. */
sealed interface BlocklistTagsUiState {
    data object Loading : BlocklistTagsUiState

    /**
     * [tags] are the TMDB keyword ids as the user has left them, saved or about to be; [search] names them and holds the
     * search results. [saveFailed] is a save the server refused: the tags stay as they are, unsaved, until the next save.
     */
    data class Ready(
        val tags: List<Int>,
        val search: KeywordSearch = KeywordSearch(),
        val saveFailed: Boolean = false,
        val saveStatus: SaveStatus = SaveStatus.Idle,
    ) : BlocklistTagsUiState

    data class Error(
        val error: SeerrError,
    ) : BlocklistTagsUiState
}

/**
 * The automatic blocklist's tags, as a page of their own that saves as it changes (#930). It reads the tags from
 * `settings/main` and names them, searches TMDB's keywords as the user types, and writes each change a moment later
 * through [SaveAsMade], the mode the editor pages share (#952).
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
        private val keywords =
            KeywordLookup(
                scope = viewModelScope,
                dispatcher = dispatcher,
                api = connection::api,
                current = { (state.value as? BlocklistTagsUiState.Ready)?.search },
                edit = { change -> editReady { it.copy(search = change(it.search)) } },
            )

        private val saveAsMade =
            SaveAsMade(
                scope = viewModelScope,
                appScope = appScope,
                dispatcher = dispatcher,
                draft = { (state.value as? BlocklistTagsUiState.Ready)?.tags },
                canSave = { true },
                write = ::write,
                // The server answers with the whole of settings/main, so the tags sent are what is adopted.
                adopt = { sent, _ -> (state.value as? BlocklistTagsUiState.Ready)?.tags != sent },
                failed = { failed -> editReady { it.copy(saveFailed = failed) } },
                isTransient = { it.toSeerrError().isTransient },
                status = { status -> editReady { it.copy(saveStatus = status) } },
            )

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
                        keywords.name(tags)
                    }.onFailure { state.value = BlocklistTagsUiState.Error(it.toSeerrError()) }
            }
        }

        /** Searches TMDB's keywords as the user types: see [KeywordLookup.search]. */
        fun search(query: String) = keywords.search(query)

        /** Adds [id] to the tags or takes it out, and saves the tags a moment after the last change. */
        fun toggle(id: Int) {
            if (state.value !is BlocklistTagsUiState.Ready) return
            editReady { it.copy(tags = if (id in it.tags) it.tags - id else it.tags + id) }
            saveAsMade.changed()
        }

        /** Sends the tags as they stand, after a save that failed: the failed changes and any made since, as one. */
        fun retry() = saveAsMade.now()

        private suspend fun write(tags: List<Int>): List<Int> {
            val csv = tags.joinToString(",")
            val body =
                SeerrMainSettingsUpdateBody(
                    blocklistedTags = csv.takeUnless { blacklistNames },
                    blacklistedTags = csv.takeIf { blacklistNames },
                )
            connection.api().updateMainSettings(body)
            return tags
        }

        /** Leaving the page with a change not yet sent sends it now, on the application's scope. */
        override fun onCleared() = saveAsMade.cleared()

        private fun editReady(transform: (BlocklistTagsUiState.Ready) -> BlocklistTagsUiState.Ready) =
            state.update { current -> (current as? BlocklistTagsUiState.Ready)?.let(transform) ?: current }
    }

/** The server's comma-separated keyword ids. */
internal fun String.tagIds(): List<Int> = split(',').mapNotNull { it.trim().toIntOrNull() }
