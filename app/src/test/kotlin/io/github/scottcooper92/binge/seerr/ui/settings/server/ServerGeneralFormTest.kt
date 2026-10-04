package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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

private val SAVED = ServerGeneralSettings(applicationTitle = "Seerr", discoverRegion = "GB", cacheImages = true)

@Composable
private fun General(
    draft: ServerGeneralSettings,
    onSave: () -> Unit = {},
) = BingeExpressiveTheme(dynamicColor = false) {
    ServerGeneralScreen(
        state = ExtrasEditorUiState.Ready(draft = draft, saved = SAVED, extras = ServerGeneralExtras()),
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = onSave),
        keyActions = ApiKeyActions(onToggleReveal = {}, onCopy = {}, onRegenerate = {}),
        onOpenDefaultPermissions = {},
    )
}

/** The server's General page: which sections start closed, and what a value of the wrong shape does to them. */
@RunWith(RobolectricTestRunner::class)
class ServerGeneralFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(draft: ServerGeneralSettings) = rule.setContent { General(draft) { saves++ } }

    @Test
    fun `application starts open and the rest start closed`() {
        show(SAVED)

        rule.onNodeWithText("Application title").assertExists()
        rule.onNodeWithText("Cache images").assertDoesNotExist()
        rule.onNode(hasSetTextAction() and hasText("GB")).assertDoesNotExist()
    }

    @Test
    fun `a region of the wrong shape opens discover and save does not save`() {
        show(SAVED.copy(discoverRegion = "Britain"))

        rule.onNode(hasSetTextAction() and hasText("Britain")).assertExists()
        rule.onNodeWithText("1 field needs attention").assertExists()
        rule.onNodeWithText("Save").performClick()

        assertEquals(0, saves)
    }

    @Test
    fun `a clean change saves`() {
        show(SAVED.copy(applicationTitle = "Requests"))

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }
}

/** The server's General page on a television: OK on a closed header opens it, and down enters its first field. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class ServerGeneralTvFocusTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `ok on discover opens it and down enters its first field`() {
        rule.setContent { General(SAVED) }
        rule.onNodeWithText("Discover").performSemanticsAction(SemanticsActions.RequestFocus)

        press(Key.DirectionCenter)
        press(Key.DirectionDown)

        rule.onNode(hasSetTextAction() and hasText("Display language", substring = true)).assertIsFocused()
    }
}
