package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The server's general settings: the main form as an editor over `settings/main`, with the API key
 * and the visitor's view loaded beside it. The server answers a write with the whole record, which
 * is what is adopted; the key lives in [extras] rather than the draft because regenerating it is
 * not an edit to save.
 */
@HiltViewModel
class ServerGeneralViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : EditorViewModel<ServerGeneralSettings>() {
        private val extrasState = MutableStateFlow(ServerGeneralExtras())
        val extras: StateFlow<ServerGeneralExtras> = extrasState.asStateFlow()

        init {
            reload()
        }

        override suspend fun load(): ServerGeneralSettings =
            coroutineScope {
                val api = connection.api()
                val profile = async { connection.profile() }
                val visitor = async { runCatching { api.publicSettings() }.getOrNull() }
                val main = api.mainSettings()
                extrasState.update { current ->
                    current.copy(apiKey = current.apiKey.copy(key = main.apiKey.orEmpty()), visitor = visitor.await()?.toVisitorView())
                }
                main.toServerGeneral(profile.await().variant)
            }

        override suspend fun write(draft: ServerGeneralSettings): ServerGeneralSettings {
            val answered = connection.api().updateMainSettings(draft.toBody())
            return answered.toServerGeneral(connection.profile().variant)
        }

        override fun canSave(draft: ServerGeneralSettings): Boolean = draft.urlValid

        fun toggleReveal() = extrasState.update { it.copy(apiKey = it.apiKey.copy(revealed = !it.apiKey.revealed)) }

        /**
         * Replaces the key. When this app is itself signed in with it, the new one is validated and
         * saved in the same step — the old one stopped working the moment the server answered, so
         * leaving the connection on it would sign the app out.
         */
        fun regenerateApiKey() {
            if (extrasState.value.apiKey.regenerating) return
            extrasState.update { it.copy(apiKey = it.apiKey.copy(regenerating = true)) }
            viewModelScope.launch {
                runCatching {
                    val key =
                        connection
                            .api()
                            .regenerateApiKey()
                            .apiKey
                            .orEmpty()
                    val current = connection.current()
                    if (current.auth is SeerrAuth.ApiKey && key.isNotEmpty()) {
                        connection.connect(current.baseUrl, SeerrAuth.ApiKey(key)).getOrThrow()
                    }
                    key
                }.onSuccess { key ->
                    extrasState.update { it.copy(apiKey = it.apiKey.copy(key = key, regenerating = false)) }
                    notify(EditorEvent.Notice(R.string.server_settings_api_key_regenerated))
                }.onFailure { failure ->
                    extrasState.update { it.copy(apiKey = it.apiKey.copy(regenerating = false)) }
                    notify(EditorEvent.Failed(failure.toSeerrError()))
                }
            }
        }
    }
