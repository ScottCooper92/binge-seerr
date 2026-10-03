package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editors are shared with the television, so the sectioned form is walked with a real D-pad on a
 * television-qualified screen: a header is a stop of its own, OK opens it, and the next press enters its
 * fields in reading order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class DvrInstanceTvFocusTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show() =
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                val draft = completeRadarr()
                DvrInstanceScreen(
                    state = ExtrasEditorUiState.Ready(draft = draft, saved = draft, extras = DvrExtras(choices = TESTED)),
                    events = emptyFlow(),
                    actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = {}),
                    onTest = {},
                    onDelete = {},
                )
            }
        }

    private fun focusHeader(title: String) = rule.onNodeWithText(title).performSemanticsAction(SemanticsActions.RequestFocus)

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `an open header is a focus stop and down enters its own fields before the next header`() {
        show()
        focusHeader("Destination")

        rule.onNodeWithText("Destination").assertIsFocused()
        press(Key.DirectionDown)

        rule.onNodeWithText("Destination").assertIsNotFocused()
        rule.onNodeWithText("Advanced").assertIsNotFocused()
    }

    @Test
    fun `ok on a header opens it and down enters its first field`() {
        show()
        focusHeader("Advanced")

        press(Key.DirectionCenter)
        rule.waitForIdle()
        press(Key.DirectionDown)

        rule.onNode(hasSetTextAction() and hasText("URL base", substring = true)).assertIsFocused()
    }
}
