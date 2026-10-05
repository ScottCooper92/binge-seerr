package io.github.scottcooper92.binge.seerr.ui.users.settings

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
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val MANAGED =
    GeneralSettings(displayName = "Ann", email = "ann@home.lan", region = "GB", canEditQuotas = true, canEditEmail = true)

@Composable
private fun General(
    draft: GeneralSettings,
    onSave: () -> Unit = {},
) = BingeExpressiveTheme(dynamicColor = false) {
    GeneralSettingsScreen(
        state = EditorUiState.Ready(draft = draft, saved = MANAGED),
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = onSave),
    )
}

/** The general page's sections: which start closed, and what a value of the wrong shape does to them. */
@RunWith(RobolectricTestRunner::class)
class GeneralSettingsFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(draft: GeneralSettings) = rule.setContent { General(draft) { saves++ } }

    @Test
    fun `discover and the quotas start closed`() {
        show(MANAGED)

        rule.onNodeWithText("Display name").assertExists()
        rule.onNodeWithText("Region", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Limit").assertDoesNotExist()
    }

    @Test
    fun `a region of the wrong shape opens discover and says what it wants`() {
        show(MANAGED.copy(region = "Britain"))

        rule.onNodeWithText("1 field needs attention").assertExists()
        rule.onNode(hasSetTextAction() and hasText("Britain")).assertExists()
    }

    @Test
    fun `a quota of the wrong shape opens the quotas and save does not save`() {
        show(MANAGED.copy(movieQuotaLimit = "five"))

        rule.onNodeWithText("Enter a whole number, or leave it blank.").assertExists()
        rule.onNodeWithText("Save").performClick()

        assertEquals(0, saves)
    }

    @Test
    fun `a changed draft with nothing wrong saves`() {
        show(MANAGED.copy(displayName = "Annie"))

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }
}

/** The general page on a television: OK on a closed header opens it, and down enters its first field. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class GeneralSettingsTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `ok on discover opens it and down enters its first field`() {
        rule.setContent { General(MANAGED) }
        rule.onNodeWithText("Discover").performSemanticsAction(SemanticsActions.RequestFocus)

        press(Key.DirectionCenter)
        press(Key.DirectionDown)

        rule.onNode(hasSetTextAction() and hasText("Display language", substring = true)).assertIsFocused()
    }
}
