package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The server's general settings: the main form as an editor over `settings/main`, with the API key, the fork (for
 * its display languages) and the server's region and language lists beside it, each list read only when its picker
 * opens. The server answers a write with the whole record, which is what is adopted; the key lives in the extras
 * rather than the draft because regenerating it is not an edit to save.
 */
@HiltViewModel
class ServerGeneralViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val listCatalog: ServerListCatalog,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ExtrasEditorViewModel<ServerGeneralSettings, ServerGeneralExtras>(ServerGeneralExtras(), dispatcher) {
        init {
            reload()
        }

        override suspend fun load(): ServerGeneralSettings =
            coroutineScope {
                val api = connection.api()
                val profile = async { connection.profile() }
                val main = api.mainSettings()
                val variant = profile.await().variant
                editExtras { current ->
                    current.copy(
                        apiKey = current.apiKey.copy(key = main.apiKey.orEmpty()),
                        variant = variant,
                    )
                }
                main.toServerGeneral(variant)
            }

        override suspend fun write(draft: ServerGeneralSettings): ServerGeneralSettings {
            val answered = connection.api().updateMainSettings(draft.toBody())
            return answered.toServerGeneral(connection.profile().variant)
        }

        override fun canSave(draft: ServerGeneralSettings): Boolean = draft.valid

        /** Reads [kind]'s list for its picker, once; a failed read can be asked for again. */
        fun loadList(kind: ServerList) {
            val held = currentExtras().lists[kind]
            if (held is ListChoices.Ready || held == ListChoices.Loading) return
            editExtras { it.copy(lists = it.lists + (kind to ListChoices.Loading)) }
            viewModelScope.launch(dispatcher) {
                val choices = runCatching { listCatalog.entries(kind) }.fold({ ListChoices.Ready(it) }, { ListChoices.Failed })
                editExtras { it.copy(lists = it.lists + (kind to choices)) }
            }
        }

        /** Names the blocklisted tags the draft holds, once each: the server keeps them as TMDB ids. */
        fun loadKeywordNames(ids: List<Int>) {
            val missing = ids.filter { it !in currentExtras().keywords.names }
            if (missing.isEmpty()) return
            viewModelScope.launch(dispatcher) {
                val api = connection.api()
                val named =
                    missing
                        .mapNotNull { id ->
                            attempt { api.keyword(id) }
                                .getOrNull()
                                ?.name
                                ?.let { id to it }
                        }.toMap()
                editExtras { it.copy(keywords = it.keywords.copy(names = it.keywords.names + named)) }
            }
        }

        /**
         * Re-reads the blocklisted tags after the tags page has saved them, into the saved record and the draft alike, so
         * the row shows what the server holds without disturbing an unsaved edit elsewhere on the page.
         */
        fun refreshBlocklistTags() {
            if (ready() == null) return
            viewModelScope.launch(dispatcher) {
                val tags = attempt { connection.api().mainSettings() }.getOrNull()?.tags ?: return@launch
                editReady { ready ->
                    ready.copy(
                        saved = ready.saved.copy(blocklist = ready.saved.blocklist?.copy(tags = tags)),
                        draft = ready.draft.copy(blocklist = ready.draft.blocklist?.copy(tags = tags)),
                    )
                }
            }
        }

        fun toggleReveal() = editExtras { it.copy(apiKey = it.apiKey.copy(revealed = !it.apiKey.revealed)) }

        /**
         * Replaces the key. When this app is itself signed in with it, the new one is validated and
         * saved in the same step — the old one stopped working the moment the server answered, so
         * leaving the connection on it would sign the app out.
         *
         * The regenerate call and the reconnect probe are reported separately: the regenerate call
         * is what actually invalidates the old key, so its result is adopted into the extras
         * regardless of whether the follow-up reconnect succeeds. Otherwise a probe failure right
         * after a successful regenerate would discard the only copy of the new key the app ever saw.
         */
        fun regenerateApiKey() {
            if (currentExtras().apiKey.regenerating) return
            editExtras { it.copy(apiKey = it.apiKey.copy(regenerating = true)) }
            viewModelScope.launch(dispatcher) {
                runCatching {
                    connection
                        .api()
                        .regenerateApiKey()
                        .apiKey
                        .orEmpty()
                }.onSuccess { key ->
                    editExtras { it.copy(apiKey = it.apiKey.copy(key = key, regenerating = false)) }
                    val current = connection.current()
                    if (current.auth is SeerrAuth.ApiKey && key.isNotEmpty()) {
                        connection.connect(current.baseUrl, SeerrAuth.ApiKey(key)).onFailure { failure ->
                            notify(EditorEvent.Failed(failure.toSeerrError()))
                            return@launch
                        }
                    }
                    notify(EditorEvent.Notice(R.string.server_settings_api_key_regenerated))
                }.onFailure { failure ->
                    editExtras { it.copy(apiKey = it.apiKey.copy(regenerating = false)) }
                    notify(EditorEvent.Failed(failure.toSeerrError()))
                }
            }
        }
    }

/** How long the tags picker waits after the last keystroke before it searches. */
internal const val KEYWORD_SEARCH_DEBOUNCE_MILLIS = 300L
