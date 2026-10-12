package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Leaving an explicit-save page with a change unsaved asks first, and so does a page that saves as it changes while its change
 * is held as invalid. A clean page, or a save-as-made one with nothing held, does not.
 */
@RunWith(RobolectricTestRunner::class)
class EditorDiscardTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var left = 0
    private var dispatcher: OnBackPressedDispatcher? = null

    private fun show(
        draft: String,
        saved: String = "saved",
        canSave: (String) -> Boolean = { true },
        saveAsMade: Boolean = false,
        footerCommit: Boolean = false,
    ) = rule.setContent {
        dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        BingeExpressiveTheme(dynamicColor = false) {
            EditorPage(
                title = "Network",
                state = EditorUiState.Ready(draft = draft, saved = saved),
                events = emptyFlow(),
                actions = EditorActions(onBack = { left++ }, onRetry = {}, onEdit = {}, onSave = {}),
                canSave = canSave,
                saveAsMade = saveAsMade,
                footerCommit = footerCommit,
                commitLabel = "Change password".takeIf { footerCommit },
            ) { value, _ -> Text(value) }
        }
    }

    private fun back() = rule.onNodeWithContentDescription("Back").performClick()

    @Test
    fun `a clean page leaves at once`() {
        show("saved")

        back()

        assertEquals(1, left)
        rule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `an unsaved change asks, and Keep editing stays`() {
        show("edited")

        back()
        rule.onNodeWithText("Discard changes?").assertExists()
        rule.onNodeWithText("Keep editing").performClick()

        assertEquals(0, left)
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onNodeWithText("edited").assertExists()
    }

    @Test
    fun `Discard leaves`() {
        show("edited")

        back()
        rule.onNodeWithText("Discard").performClick()

        assertEquals(1, left)
    }

    @Test
    fun `the system Back asks too`() {
        show("edited")

        rule.runOnUiThread { checkNotNull(dispatcher).onBackPressed() }

        rule.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, left)
    }

    @Test
    fun `a page that saves as it changes does not ask while its change can be sent`() {
        show("edited", saveAsMade = true)

        back()

        assertEquals(1, left)
    }

    /** A change that leaves the record invalid is held, not sent, so leaving would drop it without a word (#1447). */
    @Test
    fun `a page that saves as it changes asks while its change is held as invalid`() {
        show("", canSave = { it.isNotEmpty() }, saveAsMade = true)

        back()

        rule.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, left)
    }

    /** A record the server sent already invalid goes back as it came, so nothing is held and nothing is lost (#957). */
    @Test
    fun `a page that saves as it changes does not ask over a record that came invalid`() {
        show("edited", saved = "", canSave = { it.length > 6 }, saveAsMade = true)

        back()

        assertEquals(1, left)
    }

    @Test
    fun `a footer commit carries its own label, and Cancel beside it asks too`() {
        show("edited", footerCommit = true)

        rule.onNodeWithText("Change password").assertExists()
        rule.onNodeWithText("Cancel").performClick()

        rule.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, left)
    }
}
