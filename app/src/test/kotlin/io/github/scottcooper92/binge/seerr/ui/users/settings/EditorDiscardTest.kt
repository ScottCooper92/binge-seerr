package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Leaving an explicit-save page with a change unsaved asks first; a clean page, or one that saves as it changes, does not. */
@RunWith(RobolectricTestRunner::class)
class EditorDiscardTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var left = 0
    private var dispatcher: OnBackPressedDispatcher? = null

    private fun show(
        draft: String,
        saveAsMade: Boolean = false,
    ) = rule.setContent {
        dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        BingeExpressiveTheme(dynamicColor = false) {
            EditorPage(
                title = "Network",
                state = EditorUiState.Ready(draft = draft, saved = "saved"),
                events = emptyFlow(),
                actions = EditorActions(onBack = { left++ }, onRetry = {}, onEdit = {}, onSave = {}),
                saveAsMade = saveAsMade,
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
    fun `a page that saves as it changes never asks`() {
        show("edited", saveAsMade = true)

        back()

        assertEquals(1, left)
    }
}
