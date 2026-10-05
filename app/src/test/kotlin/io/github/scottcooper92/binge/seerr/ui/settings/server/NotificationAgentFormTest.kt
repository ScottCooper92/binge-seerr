package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
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
) = BingeExpressiveTheme(dynamicColor = false) {
    NotificationAgentScreen(
        state = ExtrasEditorUiState.Ready(draft = draft, saved = DISCORD_OFF, extras = AgentExtras()),
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = onSave),
        agentActions = AgentActions(onSetEnabled = {}, onSetOption = { _, _ -> }, onSetEncryption = {}, onToggleType = {}, onTest = {}),
        agent = draft.agent,
    )
}

/** An agent's page: the required settings open and marked, the rest closed, and Save held while one is missing. */
@RunWith(RobolectricTestRunner::class)
class NotificationAgentFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(draft: AgentForm) = rule.setContent { Agent(draft) { saves++ } }

    @Test
    fun `an agent that is on marks its webhook required and keeps more settings closed`() {
        show(DISCORD_OFF.copy(enabled = true))

        rule.onNode(hasSetTextAction() and hasText("Webhook URL *", substring = true)).assertExists()
        rule.onNodeWithText("More settings").assertExists()
        rule.onNode(hasSetTextAction() and hasText("Bot username", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `save with the webhook blank does not save and says it is required`() {
        show(DISCORD_OFF.copy(enabled = true))

        assertEquals(0, rule.onAllNodesWithText("Required").fetchSemanticsNodes().size)
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        assertEquals(0, saves)
        rule.onNodeWithText("Required").assertExists()
    }

    @Test
    fun `an agent that is off saves half-typed`() {
        show(DISCORD_OFF.copy(options = mapOf(AgentOption.DiscordBotUsername to "Seerr")))

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }
}

/** An agent's page on a television: OK on the closed more-settings header opens it, and down enters its first field. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class NotificationAgentTvFocusTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `ok on more settings opens it and down enters its first field`() {
        rule.setContent { Agent(DISCORD_OFF) }
        rule.onNodeWithText("More settings").performSemanticsAction(SemanticsActions.RequestFocus)

        press(Key.DirectionCenter)
        press(Key.DirectionDown)

        rule.onNode(hasSetTextAction() and hasText("Bot username", substring = true)).assertIsFocused()
    }
}
