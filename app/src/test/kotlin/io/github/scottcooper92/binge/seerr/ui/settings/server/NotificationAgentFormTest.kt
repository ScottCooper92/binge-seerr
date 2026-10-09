package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val DISCORD_OFF = AgentForm(ServerAgent.Discord)

@Composable
private fun Agent(
    draft: AgentForm,
    onSave: () -> Unit = {},
    onTest: () -> Unit = {},
) = BingeExpressiveTheme(dynamicColor = false) {
    NotificationAgentScreen(
        state = ExtrasEditorUiState.Ready(draft = draft, saved = DISCORD_OFF, extras = AgentExtras()),
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = onSave),
        agentActions = AgentActions(onSetEnabled = {}, onSetOption = { _, _ -> }, onSetEncryption = {}, onToggleType = {}, onTest = onTest),
        agent = draft.agent,
    )
}

/** An agent's page: a required setting left blank says so in its row and holds Save, and the test waits on it too. */
@RunWith(RobolectricTestRunner::class)
class NotificationAgentFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0
    private var tests = 0

    private fun show(draft: AgentForm) = rule.setContent { Agent(draft, onSave = { saves++ }, onTest = { tests++ }) }

    @Test
    fun `an agent that is on with its webhook blank says it is required and can't be saved`() {
        show(DISCORD_OFF.copy(enabled = true))

        rule.onNodeWithText("Required").assertExists()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNode(hasText("Send a test notification") and hasClickAction()).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `an agent that is off saves half-typed and marks nothing`() {
        show(DISCORD_OFF.copy(options = mapOf(AgentOption.DiscordBotUsername to "Seerr")))

        rule.onNodeWithText("Required").assertDoesNotExist()
        rule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, saves)
    }

    @Test
    fun `the events are switch rows, requests and issues apart`() {
        show(DISCORD_OFF)

        rule.onNodeWithText("Notify about requests", ignoreCase = true).performScrollTo().assertExists()
        rule.onNodeWithText("Notify about issues", ignoreCase = true).performScrollTo().assertExists()
    }

    @Test
    fun `a secret option's hint shows in its sheet`() {
        show(AgentForm(ServerAgent.Pushover))

        rule.onNode(hasText("Application token") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        rule.onNodeWithText("Register an application with Pushover and use the API token it gives you.").assertExists()
    }

    @Test
    fun `a port outside the range shows its error in the sheet and keeps Done off`() {
        show(AgentForm(ServerAgent.Email))

        rule.onNode(hasText("SMTP port") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNode(hasSetTextAction()).performTextReplacement("70000")

        rule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun `a complete agent sends a test`() {
        show(DISCORD_OFF.copy(enabled = true, options = mapOf(AgentOption.DiscordWebhookUrl to "https://discord.example/hook")))

        rule
            .onNode(
                hasText("Send a test notification") and hasClickAction(),
            ).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, tests)
    }
}

/** An agent's page on a television: OK on a text row opens its editor. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class NotificationAgentTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `ok on the bot username row opens its editor`() {
        rule.setContent { Agent(DISCORD_OFF.copy(options = mapOf(AgentOption.DiscordBotUsername to "Seerr"))) }
        rule.onNode(hasText("Bot username") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction() and hasText("Seerr")).assertExists()
    }
}
