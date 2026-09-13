package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val SOUNDS_DEBOUNCE_MILLIS = 600L

/**
 * One notification agent's settings: on or off, the events it is sent, and its own options. A test
 * sends a notification through the draft as typed, so a change can be checked before it is saved.
 * For Pushover, the application token typed is asked for its sounds once it stops changing.
 */
@HiltViewModel(assistedFactory = NotificationAgentViewModel.Factory::class)
class NotificationAgentViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @Assisted val agent: ServerAgent,
    ) : EditorViewModel<AgentForm>() {
        private val extrasState = MutableStateFlow(AgentExtras())
        val extras: StateFlow<AgentExtras> = extrasState.asStateFlow()

        internal var soundsDebounceMillis = SOUNDS_DEBOUNCE_MILLIS

        init {
            reload()
            if (agent == ServerAgent.Pushover) viewModelScope.launch { followPushoverToken() }
        }

        override suspend fun load(): AgentForm = connection.api().notificationAgent(agent.segment).toForm(agent)

        override suspend fun write(draft: AgentForm): AgentForm =
            connection.api().updateNotificationAgent(agent.segment, draft.toDto()).toForm(agent)

        override fun canSave(draft: AgentForm): Boolean = draft.valid

        fun setOption(
            option: AgentOption,
            value: String,
        ) = edit { it.copy(options = it.options + (option to value)) }

        fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

        fun toggleType(bit: Int) = edit { it.copy(types = it.types xor bit) }

        /** Sends a test through the draft; the server answers only with a status, so success is the absence of one. */
        fun test() {
            val draft = ready()?.draft ?: return
            if (extrasState.value.testing) return
            extrasState.update { it.copy(testing = true) }
            viewModelScope.launch {
                val outcome =
                    runCatching {
                        val response = connection.api().testNotificationAgent(agent.segment, draft.toDto())
                        if (!response.isSuccessful) throw HttpException(response)
                    }
                extrasState.update { it.copy(testing = false) }
                outcome
                    .onSuccess { notify(EditorEvent.Notice(R.string.server_settings_agent_tested)) }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        private suspend fun followPushoverToken() {
            if (!connection.profile().hasPushoverSounds) return
            uiState
                .map { (it as? EditorUiState.Ready<AgentForm>)?.draft?.option(AgentOption.PushoverAccessToken)?.trim() }
                .distinctUntilChanged()
                .collectLatest { token ->
                    if (token.isNullOrEmpty()) {
                        extrasState.update { it.copy(sounds = emptyList()) }
                        return@collectLatest
                    }
                    delay(soundsDebounceMillis)
                    val sounds = runCatching { connection.api().pushoverSounds(token).map { it.toSound() } }.getOrDefault(emptyList())
                    extrasState.update { it.copy(sounds = sounds) }
                }
        }

        @AssistedFactory
        interface Factory {
            fun create(agent: ServerAgent): NotificationAgentViewModel
        }
    }
