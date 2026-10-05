package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val EMAIL_ONLY = NotificationSettings(agents = mapOf(NotificationAgent.Email to AgentSettings(enabled = true)))

@Composable
private fun Notifications(
    draft: NotificationSettings,
    onSave: () -> Unit = {},
) = BingeExpressiveTheme(dynamicColor = false) {
    NotificationsSettingsScreen(
        state = EditorUiState.Ready(draft = draft, saved = EMAIL_ONLY),
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = onSave),
    )
}

@Composable
private fun EditableNotifications(initial: NotificationSettings) {
    var draft by remember { mutableStateOf(initial) }
    BingeExpressiveTheme(dynamicColor = false) {
        NotificationsSettingsScreen(
            state = EditorUiState.Ready(draft = draft, saved = initial),
            events = emptyFlow(),
            actions = EditorActions(onBack = {}, onRetry = {}, onEdit = { draft = it(draft) }, onSave = {}),
        )
    }
}

/** The notifications page's sections: an agent that is on starts open, and a bad Discord ID opens its own. */
@RunWith(RobolectricTestRunner::class)
class NotificationsFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(draft: NotificationSettings) = rule.setContent { Notifications(draft) { saves++ } }

    @Test
    fun `an agent that is on starts open and one that is off starts closed`() {
        show(EMAIL_ONLY)

        rule.onNodeWithText("PGP public key", substring = true).assertExists()
        rule.onNodeWithText("Discord user ID", substring = true).assertDoesNotExist()
    }

    @Test
    fun `clearing the only field of an agent without a switch keeps its section open`() {
        val saved = EMAIL_ONLY.update(NotificationAgent.Pushbullet) { it.copy(fields = mapOf(AgentField.PushbulletToken to "o.abc")) }
        rule.setContent { EditableNotifications(saved) }

        val token = hasSetTextAction() and hasText("Access token", substring = true)
        rule.onNode(token).assertExists()
        rule.onNode(token).performTextClearance()

        rule.onNode(token).assertExists()
    }

    @Test
    fun `a bad discord id opens the discord section and save does not save`() {
        show(EMAIL_ONLY.update(NotificationAgent.Discord) { it.copy(fields = mapOf(AgentField.DiscordId to "ann#1234")) })

        rule.onNodeWithText("1 field needs attention").assertExists()
        rule.onNode(hasSetTextAction() and hasText("ann#1234")).assertExists()
        rule.onNodeWithText("Save").performClick()

        assertEquals(0, saves)
    }

    @Test
    fun `a clean change saves`() {
        show(EMAIL_ONLY.update(NotificationAgent.Email) { it.copy(types = NotificationType.MediaApproved.bit) })

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }
}

/** The notifications page on a television: a closed agent is one stop, and down moves on to the next agent. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class NotificationsTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `down from a closed agent lands on the next agent's header`() {
        rule.setContent { Notifications(EMAIL_ONLY) }
        rule.onNodeWithText("Discord").performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()

        rule.onNodeWithText("Discord").assertIsNotFocused()
        rule.onNodeWithText("Telegram").assertIsFocused()
    }
}
