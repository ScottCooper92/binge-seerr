package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val EMAIL_ONLY = NotificationSettings(agents = mapOf(NotificationAgent.Email to AgentSettings(enabled = true)))

/** The notifications page's groups: every agent but Web push, its values in rows, and its events while it is on. */
@RunWith(RobolectricTestRunner::class)
class NotificationsFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0
    private var draft by mutableStateOf(EMAIL_ONLY)

    private fun show(initial: NotificationSettings) {
        draft = initial
        rule.setContent { Notifications(saved = initial) }
    }

    @Composable
    private fun Notifications(saved: NotificationSettings) {
        val current = remember { saved }
        BingeExpressiveTheme(dynamicColor = false) {
            NotificationsSettingsScreen(
                state = EditorUiState.Ready(draft = draft, saved = current),
                events = emptyFlow(),
                actions = EditorActions(onBack = {}, onRetry = {}, onEdit = { draft = it(draft) }, onSave = { saves++ }),
            )
        }
    }

    @Test
    fun `every agent but web push has a group, and only one that is on lists its events`() {
        show(EMAIL_ONLY)

        listOf("EMAIL", "DISCORD", "PUSHBULLET", "PUSHOVER", "TELEGRAM").forEach { rule.onNodeWithText(it).performScrollTo() }
        rule.onNodeWithText("WEB PUSH").assertDoesNotExist()
        rule.onNodeWithText("Request approved").assertExists()
        rule.onNodeWithText("The server has Discord notifications turned off", substring = true).assertExists()
    }

    @Test
    fun `a bad discord id is flagged in its row and save does not save`() {
        show(EMAIL_ONLY.copy(discordIds = listOf("ann#1234")).update(NotificationAgent.Email) { it.copy(types = 4) })

        rule.onNodeWithText("Enter the numeric ID, not a username.").performScrollTo()
        rule.onNodeWithText("Save").performClick()

        assertEquals(0, saves)
    }

    @Test
    fun `a telegram chat sent events needs its chat id`() {
        show(EMAIL_ONLY.update(NotificationAgent.Telegram) { it.copy(enabled = true, types = NotificationType.MediaApproved.bit) })

        rule.onNodeWithText("Required").performScrollTo()
        assertEquals(false, draft.valid)
    }

    @Test
    fun `a second discord id is added from its own row`() {
        show(EMAIL_ONLY.copy(discordIds = listOf("1234"), multipleDiscordIds = true))

        rule.onNodeWithText("Add user ID").performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("5678")
        rule.onNodeWithText("Done").performClick()

        assertEquals(listOf("1234", "5678"), draft.discordIds)
    }

    @Test
    fun `clearing a discord id removes it`() {
        show(EMAIL_ONLY.copy(discordIds = listOf("1234", "5678"), multipleDiscordIds = true))

        rule.onNodeWithText("1234").performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("")
        rule.onNodeWithText("Done").performClick()

        assertEquals(listOf("5678"), draft.discordIds)
    }

    @Test
    fun `clearing a set pushover app token turns pushover off`() {
        show(EMAIL_ONLY.set(AgentField.PushoverAppToken, "azGDORePK8gMaC0QOYAMyEEuzJnyUi"))

        rule.onNodeWithText("azGDORePK8gMaC0QOYAMyEEuzJnyUi").performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("")
        rule.onNodeWithText("Done").performClick()

        assertEquals("", draft.field(AgentField.PushoverAppToken))
        assertEquals(false, draft.isOn(NotificationAgent.Pushover))
    }

    @Test
    fun `a pushover sound is picked from the application's sounds, the device's own first`() {
        show(
            EMAIL_ONLY
                .set(AgentField.PushoverAppToken, "azGDORePK8gMaC0QOYAMyEEuzJnyUi")
                .copy(pushoverSounds = listOf(PushoverSoundChoice("bike", "Bike"))),
        )

        rule.onNodeWithText("Device default").performScrollTo().performClick()
        rule.onNodeWithText("Bike").performClick()

        assertEquals("bike", draft.field(AgentField.PushoverSound))
    }

    @Test
    fun `a clean change saves`() {
        show(EMAIL_ONLY)
        draft = EMAIL_ONLY.update(NotificationAgent.Email) { it.copy(types = NotificationType.MediaApproved.bit) }

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }
}
