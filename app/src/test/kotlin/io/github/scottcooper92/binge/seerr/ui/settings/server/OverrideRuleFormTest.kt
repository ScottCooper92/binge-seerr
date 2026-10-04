package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
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
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
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

private val RULE_EXTRAS =
    OverrideRuleExtras(
        instances = listOf(DvrSummary(1, ServiceType.Radarr, "Radarr", "radarr.lan:7878", is4k = false, isDefault = true)),
        users = listOf(Choice(1, "Ann")),
        choices = TESTED,
    )

private val ON_RADARR = OverrideRuleForm(serviceType = ServiceType.Radarr, serviceId = 1)

/** The override rule form's page behaviour: what Save does when the rule is short of something, and where it takes the user. */
@RunWith(RobolectricTestRunner::class)
class OverrideRuleFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(draft: OverrideRuleForm) =
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                OverrideRuleScreen(
                    state = ExtrasEditorUiState.Ready(draft = draft, saved = OverrideRuleForm(), extras = RULE_EXTRAS),
                    events = emptyFlow(),
                    actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = { saves++ }),
                    ruleActions = OverrideRuleActions(onSelectInstance = {}, onToggleUser = {}, onToggleTag = {}, onDelete = {}),
                )
            }
        }

    @Test
    fun `the instance picker is marked required`() {
        show(ON_RADARR)

        rule.onNodeWithText("Server *").assertExists()
    }

    @Test
    fun `save without an instance does not save and says it is missing`() {
        show(OverrideRuleForm(genres = "16", profileId = 4))

        assertEquals(0, rule.onAllNodesWithText("Required").fetchSemanticsNodes().size)
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        assertEquals(0, saves)
        rule.onNodeWithText("1 field needs attention").assertIsDisplayed()
        rule.onNodeWithText("Required").assertIsDisplayed()
    }

    @Test
    fun `a failed save opens the instance section the user had closed`() {
        show(OverrideRuleForm(genres = "16", profileId = 4))

        rule.onNodeWithText("Applies to").performClick()
        rule.onNodeWithText("Server *").assertDoesNotExist()
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Server *").assertExists()
        rule.onNodeWithText("Required").assertIsDisplayed()
    }

    @Test
    fun `save without a condition or an override does not save and says what each section needs`() {
        show(ON_RADARR)

        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        assertEquals(0, saves)
        rule.onNodeWithText("Add at least one condition: a user, genre, language or keyword.").assertExists()
        rule.onNodeWithText("Pick at least one override: a quality profile, root folder or tag.").assertExists()
    }

    @Test
    fun `a failed save opens the conditions section the user had closed`() {
        show(ON_RADARR.copy(profileId = 4))

        rule.onNodeWithText("When a request matches").performClick()
        rule.onNodeWithText("Genres").assertDoesNotExist()
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        assertEquals(0, saves)
        rule.onNodeWithText("Add at least one condition: a user, genre, language or keyword.").assertIsDisplayed()
        rule.onNodeWithText("Pick at least one override: a quality profile, root folder or tag.").assertDoesNotExist()
    }

    @Test
    fun `save with a condition, an override and an instance saves`() {
        show(ON_RADARR.copy(genres = "16", profileId = 4))

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }
}

/** The rule form on a television: each header is a stop of its own, and down enters its fields in order. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class OverrideRuleTvFocusTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show() =
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                OverrideRuleScreen(
                    state = ExtrasEditorUiState.Ready(draft = ON_RADARR, saved = ON_RADARR, extras = RULE_EXTRAS),
                    events = emptyFlow(),
                    actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = {}),
                    ruleActions = OverrideRuleActions(onSelectInstance = {}, onToggleUser = {}, onToggleTag = {}, onDelete = {}),
                )
            }
        }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `a closed header is one stop and down moves to the next header`() {
        show()
        rule.onNodeWithText("When a request matches").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)

        press(Key.DirectionDown)

        rule.onNodeWithText("When a request matches").assertIsNotFocused()
        rule.onNodeWithText("Send it with").assertIsFocused()
    }

    @Test
    fun `down from an open header enters its first field`() {
        show()
        rule.onNodeWithText("When a request matches").performSemanticsAction(SemanticsActions.RequestFocus)

        press(Key.DirectionDown)
        press(Key.DirectionDown)

        rule.onNode(hasSetTextAction() and hasText("Genres", substring = true)).assertIsFocused()
    }
}
