package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrTallComponentPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationType
import kotlinx.coroutines.flow.emptyFlow

private val TYPES_REQUESTS = listOf(NotificationType.MediaApproved, NotificationType.MediaAvailable, NotificationType.MediaDeclined).bits()
private val TYPES_ISSUES = listOf(NotificationType.IssueComment, NotificationType.IssueResolved).bits()

private fun List<NotificationType>.bits() = fold(0) { mask, type -> mask or type.bit }

/**
 * Settings › Notifications: the list of the server's agents, then one agent's page. The page renders
 * every agent from one option table, so the frames pick the kinds of control that table produces:
 * the encryption picker, required text fields, a gated pair, a choice from the server, a multi-line body.
 */
class NotificationAgentsScreenshotTest {
    /** Each agent as the list shows it: on in green, off, and one whose settings could not be read. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun listLayout() = AgentsFrame(AgentsUiState.Ready(agents()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = AgentsFrame(AgentsUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = AgentsFrame(AgentsUiState.Error(SeerrError.Unreachable))
}

class NotificationAgentScreenshotTest {
    /** Email: the most options, with the encryption picker, masked secrets and a required port. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun emailLayout() = AgentFrame(agentReady(emailForm()), ServerAgent.Email)

    /** A Discord agent that is on with its webhook blank: the row says it is required, and Save stays off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun discordMissingRequired() =
        AgentFrame(agentReady(AgentForm(ServerAgent.Discord, enabled = true, types = TYPES_REQUESTS)), ServerAgent.Discord)

    /** The same agent off is saved half-typed, so nothing is marked. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun discordOff() = AgentFrame(agentReady(AgentForm(ServerAgent.Discord)), ServerAgent.Discord)

    /** The webhook's JSON body is a multi-line field. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun webhookPayload() = AgentFrame(agentReady(webhookForm()), ServerAgent.Webhook)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun emailSpanish() = AgentFrame(agentReady(emailForm()), ServerAgent.Email)

    /** At 1.5x and 2x text the pinned Cancel and Save bar must still fit. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun emailLargeText() = AgentFrame(agentReady(emailForm()), ServerAgent.Email)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun testing() = AgentFrame(agentReady(emailForm(), AgentExtras(testing = true)), ServerAgent.Email)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = AgentFrame(ExtrasEditorUiState.Loading, ServerAgent.Slack)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = AgentFrame(ExtrasEditorUiState.Error(SeerrError.Unauthorized), ServerAgent.Slack)
}

private fun agents() =
    listOf(
        AgentSummary(ServerAgent.Email, enabled = true),
        AgentSummary(ServerAgent.Discord, enabled = true),
        AgentSummary(ServerAgent.Telegram, enabled = false),
        AgentSummary(ServerAgent.Pushover, enabled = false),
        AgentSummary(ServerAgent.Slack, enabled = null),
        AgentSummary(ServerAgent.Ntfy, enabled = false),
        AgentSummary(ServerAgent.Webhook, enabled = true),
        AgentSummary(ServerAgent.WebPush, enabled = false),
    )

private fun emailForm() =
    AgentForm(
        agent = ServerAgent.Email,
        enabled = true,
        types = TYPES_REQUESTS or TYPES_ISSUES,
        options =
            mapOf(
                AgentOption.EmailFrom to "binge@home.lan",
                AgentOption.EmailSenderName to "Binge",
                AgentOption.EmailSmtpHost to "smtp.home.lan",
                AgentOption.EmailSmtpPort to "587",
                AgentOption.EmailRequireTls to "true",
                AgentOption.EmailAuthUser to "binge",
                AgentOption.EmailAuthPass to "hunter2hunter2",
            ),
    )

private fun ntfyForm() =
    AgentForm(
        agent = ServerAgent.Ntfy,
        enabled = true,
        types = TYPES_REQUESTS,
        options =
            mapOf(
                AgentOption.NtfyUrl to "https://ntfy.sh",
                AgentOption.NtfyTopic to "binge",
                AgentOption.NtfyAuthByPassword to "true",
                AgentOption.NtfyUsername to "scott",
                AgentOption.NtfyPassword to "correcthorse",
            ),
    )

private fun pushoverForm() =
    AgentForm(
        agent = ServerAgent.Pushover,
        enabled = true,
        types = TYPES_REQUESTS,
        options =
            mapOf(
                AgentOption.PushoverAccessToken to "a1b2c3d4e5f6",
                AgentOption.PushoverUserToken to "u1v2w3x4y5z6",
                AgentOption.PushoverSound to "bike",
            ),
    )

private fun webhookForm() =
    AgentForm(
        agent = ServerAgent.Webhook,
        enabled = true,
        types = TYPES_ISSUES,
        options =
            mapOf(
                AgentOption.WebhookUrl to "https://hooks.home.lan/binge",
                AgentOption.WebhookJsonPayload to "{\n  \"subject\": \"{{subject}}\",\n  \"message\": \"{{message}}\"\n}",
            ),
    )

/** An agent's optional settings, which sit below the fold of the page. */
class NotificationAgentSectionsScreenshotTest {
    /** Email's optional half: the sender name, the encryption picker, the credentials and the PGP key. */
    @PreviewTest
    @SeerrTallComponentPreviews
    @Composable
    fun emailMoreSettings() = MoreSettingsFrame(emailForm())

    /** ntfy signs in one way: the password pair is live and the token field follows its switch off. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun ntfyPasswordAuth() = MoreSettingsFrame(ntfyForm())

    /** Pushover offers the sounds its application answered with, where other agents type their options. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun pushoverWithSounds() =
        MoreSettingsFrame(
            pushoverForm(),
            AgentExtras(sounds = listOf(PushoverSound("pushover", "Pushover (default)"), PushoverSound("bike", "Bike"))),
        )
}

private val SECTION_WIDTH = 411.dp
private val SECTION_PADDING = 16.dp

@Composable
private fun MoreSettingsFrame(
    form: AgentForm,
    extras: AgentExtras = AgentExtras(),
) = Column(modifier = Modifier.width(SECTION_WIDTH).padding(SECTION_PADDING)) {
    OptionGroup(
        required = false,
        draft = form,
        extras = extras,
        enabled = true,
        actions = AgentActions(onSetEnabled = {}, onSetOption = { _, _ -> }, onSetEncryption = {}, onToggleType = {}, onTest = {}),
    )
}

private fun agentReady(
    form: AgentForm,
    extras: AgentExtras = AgentExtras(),
) = ExtrasEditorUiState.Ready(draft = form, saved = form, extras = extras)

private fun <T> noActions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

@Composable
private fun AgentsFrame(state: AgentsUiState) =
    NotificationAgentsScreen(state = state, actions = AgentsActions(onBack = {}, onRetry = {}, onOpenAgent = {}))

@Composable
private fun AgentFrame(
    state: ExtrasEditorUiState<AgentForm, AgentExtras>,
    agent: ServerAgent,
) = NotificationAgentScreen(
    state = state,
    events = emptyFlow(),
    actions = noActions(),
    agentActions = AgentActions(onSetEnabled = {}, onSetOption = { _, _ -> }, onSetEncryption = {}, onToggleType = {}, onTest = {}),
    agent = agent,
)
