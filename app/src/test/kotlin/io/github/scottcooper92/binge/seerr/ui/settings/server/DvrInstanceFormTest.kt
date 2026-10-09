package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal val TESTED = DvrChoices(listOf(Choice(4, "HD")), listOf("/movies"), emptyList(), null)

internal fun completeRadarr() =
    DvrForm.blank(ServiceType.Radarr).copy(name = "Radarr", host = "radarr.lan", apiKey = "key-1234", profileId = 4, rootFolder = "/movies")

/** The instance form: Save follows the draft's validity, the destination waits on a test, and values are checked in their sheets. */
@RunWith(RobolectricTestRunner::class)
class DvrInstanceFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0
    private var tests = 0

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
                actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = { saves++ }),
                onTest = { tests++ },
                onDelete = {},
            )
        }
    }

    private fun save() = rule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `a complete instance saves`() {
        show(completeRadarr())

        save()

        assertEquals(1, saves)
    }

    @Test
    fun `an instance missing its name says so in the row and can't be saved`() {
        show(completeRadarr().copy(name = ""))

        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Required").assertExists()
    }

    @Test
    fun `before a test the destination shows what is saved and can't be opened`() {
        show(completeRadarr().copy(profileName = "HD-1080p"), choices = null)

        rule.onNodeWithText("HD-1080p").performScrollTo().assertExists()
        rule.onNode(hasText("Quality profile") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun `the test row runs the test`() {
        show(completeRadarr(), choices = null)

        rule.onNode(hasText("Test the connection") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, tests)
    }

    @Test
    fun `a port out of range keeps done off`() {
        show(completeRadarr())

        rule.onNode(hasText("Port") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNode(hasSetTextAction()).performTextReplacement("78x8")

        rule.onNodeWithText("Enter a port between 1 and 65535.").assertExists()
        rule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun `the api key row shows only its last characters`() {
        show(completeRadarr())

        rule.onNodeWithText("•••• 1234").performScrollTo().assertExists()
    }

    @Test
    fun `a sonarr has its anime destination and new-season monitoring`() {
        show(DvrForm.blank(ServiceType.Sonarr).copy(name = "Sonarr", host = "s.lan", apiKey = "k"))

        rule.onNodeWithText("Anime", ignoreCase = true).performScrollTo().assertExists()
        rule.onNodeWithText("Monitor new seasons").performScrollTo().assertExists()
    }
}

/** The instance form on a television: OK on a text row opens its editor. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class DvrInstanceTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `ok on the name row opens its editor`() {
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
        rule.onNode(hasText("Name") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction() and hasText("Radarr")).assertExists()
    }
}
