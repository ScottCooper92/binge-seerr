package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

internal val TESTED = DvrChoices(listOf(Choice(4, "HD")), listOf("/movies"), emptyList(), null)

internal fun completeRadarr() =
    DvrForm.blank(ServiceType.Radarr).copy(name = "Radarr", host = "radarr.lan", apiKey = "key", profileId = 4, rootFolder = "/movies")

/** The instance form's page behaviour: what Save does with issues, and where it takes the user. */
@RunWith(RobolectricTestRunner::class)
class DvrInstanceFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0
    private var backs = 0

    private fun show(
        draft: DvrForm,
        choices: DvrChoices? = TESTED,
    ) = rule.setContent {
        BingeExpressiveTheme(dynamicColor = false) {
            DvrInstanceScreen(
                state =
                    ExtrasEditorUiState.Ready(
                        draft = draft,
                        saved = DvrForm.blank(draft.type),
                        extras = DvrExtras(choices = choices),
                    ),
                events = emptyFlow(),
                actions = EditorActions(onBack = { backs++ }, onRetry = {}, onEdit = {}, onSave = { saves++ }),
                onTest = {},
                onDelete = {},
            )
        }
    }

    @Test
    fun `save with a missing required field does not save and says what is missing`() {
        show(completeRadarr().copy(name = "", host = ""))

        rule.onNodeWithText("Save").performClick()

        assertEquals(0, saves)
        rule.onNodeWithText("2 fields need attention").assertIsDisplayed()
        assertEquals(2, rule.onAllNodesWithText("Required").fetchSemanticsNodes().size)
    }

    @Test
    fun `required fields are not flagged before save is tried`() {
        show(completeRadarr().copy(name = ""))

        assertEquals(0, rule.onAllNodesWithText("Required").fetchSemanticsNodes().size)
    }

    @Test
    fun `a failed save puts focus in the first invalid field`() {
        show(completeRadarr().copy(name = "", host = ""))

        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        rule.onAllNodes(hasSetTextAction())[0].assertIsFocused()
    }

    @Test
    fun `a wrong value shows its message as soon as it is typed`() {
        show(completeRadarr().copy(port = "78x8"))

        rule.onNodeWithText("Enter a port between 1 and 65535.").assertIsDisplayed()
    }

    @Test
    fun `a failed save opens a collapsed section and scrolls its field into view`() {
        show(completeRadarr().copy(externalUrl = "ftp://x"))

        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Enter a web address that starts with http:// or https://.").assertIsDisplayed()
    }

    @Test
    fun `a bad value in a closed section opens it before save is tried`() {
        show(completeRadarr().copy(externalUrl = "ftp://x"))

        rule.onNodeWithText("1 field needs attention").assertExists()
        rule.onNodeWithText("External URL").assertExists()
    }

    @Test
    fun `save with nothing to fix saves`() {
        show(completeRadarr())

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }

    @Test
    fun `cancel and save stay on screen however long the form`() {
        show(completeRadarr())

        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun `cancel leaves`() {
        show(completeRadarr())

        rule.onNodeWithText("Cancel").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `an untested instance names the step that is missing`() {
        show(DvrForm.blank(ServiceType.Radarr).copy(name = "R", host = "h", apiKey = "k"), choices = null)

        rule.onNodeWithText("Save").performClick()

        assertEquals(0, saves)
        rule.onNodeWithText("1 field needs attention").assertIsDisplayed()
    }
}
