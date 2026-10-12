package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.seerr.attempt
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.CrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.NoOpAnalytics
import com.cooper.binge.companion.seerr.telemetry.NoOpCrashBreadcrumbs
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val SOUNDS_DEBOUNCE_MILLIS = 600L

/**
 * One notification agent's settings: on or off, the events it is sent, and its own options. The page saves as it
 * changes (#930), each write the saved record with the change in it; an agent that is on is held, unsent, while a setting
 * it can't send without is blank. A test sends a notification through the draft as it stands, and stays a send. For
 * Pushover, the application token typed is asked for its sounds once it stops changing.
 */
@HiltViewModel(assistedFactory = NotificationAgentViewModel.Factory::class)
class NotificationAgentViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted val agent: ServerAgent,
        private val analytics: Analytics = NoOpAnalytics,
        @ApplicationScope appScope: CoroutineScope,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    ) : ExtrasEditorViewModel<AgentForm, AgentExtras>(AgentExtras(), dispatcher) {
        internal var soundsDebounceMillis = SOUNDS_DEBOUNCE_MILLIS

        override val saveAsMadeScope: CoroutineScope = appScope

        init {
            reload()
            if (agent == ServerAgent.Pushover) viewModelScope.launch(dispatcher) { followPushoverToken() }
            if (agent == ServerAgent.Telegram) viewModelScope.launch(dispatcher) { withholdTopic() }
        }

        /** Overseerr never had a Telegram topic, so its row would be one the server drops. */
        private suspend fun withholdTopic() {
            if (!connection.profile().hasTelegramTopics) {
                editExtras { it.copy(withheld = it.withheld + AgentOption.TelegramMessageThreadId) }
            }
        }

        override suspend fun load(): AgentForm = connection.api().notificationAgent(agent.segment).toForm(agent)

        override suspend fun write(draft: AgentForm): AgentForm {
            crashBreadcrumbs.log("updating ${agent.segment} notification agent")
            val form = connection.api().updateNotificationAgent(agent.segment, draft.toDto()).toForm(agent)
            analytics.event(
                AnalyticsEvents.NOTIFICATION_AGENT_CHANGED,
                mapOf(AnalyticsEvents.PARAM_AGENT to agent.segment, AnalyticsEvents.PARAM_ACTION to "updated"),
            )
            return form
        }

        override fun canSave(draft: AgentForm): Boolean = draft.valid

        /**
         * Turning on an option that excludes another turns that one off, so the two can never be
         * saved together. Done here rather than by disabling the other switch: a server whose
         * options already hold both — which this app could write before this — would then have no
         * control left to fix it with.
         */
        fun setOption(
            option: AgentOption,
            value: String,
        ) = edit { draft ->
            val excluded = option.excludedBy?.takeIf { value.toBoolean() }
            val cleared = excluded?.let { mapOf(it to false.toString()) }.orEmpty()
            draft.copy(options = draft.options + (option to value) + cleared)
        }

        /** Email's TLS choice writes all three keys at once, so the three can never disagree. */
        fun setEncryption(encryption: EmailEncryption) = edit { draft -> draft.copy(options = draft.options + encryption.flags) }

        fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

        fun toggleType(bit: Int) = edit { it.copy(types = it.types xor bit) }

        /** Sends a test through the draft; the server answers only with a status, so success is the absence of one. */
        fun test() {
            val draft = ready()?.draft ?: return
            if (currentExtras().testing) return
            editExtras { it.copy(testing = true) }
            crashBreadcrumbs.log("testing ${agent.segment} notification agent")
            viewModelScope.launch(dispatcher) {
                val outcome =
                    attempt {
                        val response = connection.api().testNotificationAgent(agent.segment, draft.toDto())
                        if (!response.isSuccessful) throw HttpException(response)
                    }
                editExtras { it.copy(testing = false) }
                outcome
                    .onSuccess {
                        analytics.event(
                            AnalyticsEvents.NOTIFICATION_AGENT_CHANGED,
                            mapOf(AnalyticsEvents.PARAM_AGENT to agent.segment, AnalyticsEvents.PARAM_ACTION to "tested"),
                        )
                        notify(EditorEvent.Notice(R.string.server_settings_agent_tested))
                    }.onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        private suspend fun followPushoverToken() {
            if (!connection.profile().hasPushoverSounds) return
            uiState
                .map { (it as? ExtrasEditorUiState.Ready<AgentForm, AgentExtras>)?.draft?.option(AgentOption.PushoverAccessToken)?.trim() }
                .distinctUntilChanged()
                .collectLatest { token ->
                    if (token.isNullOrEmpty()) {
                        editExtras { it.copy(sounds = emptyList()) }
                        return@collectLatest
                    }
                    delay(soundsDebounceMillis)
                    // attempt, not runCatching: a keystroke cancels this fetch, and a swallowed cancellation would go on
                    // to empty the picker until the next one answered (#1025).
                    val sounds = attempt { connection.api().pushoverSounds(token).map { it.toSound() } }.getOrDefault(emptyList())
                    editExtras { it.copy(sounds = sounds) }
                }
        }

        @AssistedFactory
        interface Factory {
            fun create(agent: ServerAgent): NotificationAgentViewModel
        }
    }
