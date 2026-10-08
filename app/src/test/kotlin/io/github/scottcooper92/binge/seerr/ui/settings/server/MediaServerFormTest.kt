package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.AnnotatedString
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val JELLYFIN = MediaServerForm(kind = MediaServerKind.Jellyfin, host = "jf.lan", port = "8096", forgotPasswordUrl = "")

@Composable
private fun MediaServer(
    draft: MediaServerForm,
    extras: MediaServerExtras = MediaServerExtras(),
    onSave: () -> Unit = {},
) = BingeExpressiveTheme(dynamicColor = false) {
    MediaServerScreen(
        state = ExtrasEditorUiState.Ready(draft = draft, saved = JELLYFIN, extras = extras),
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = onSave),
        serverActions =
            MediaServerActions(
                onSetLibraryEnabled = { _, _ -> },
                onSyncLibraries = {},
                onStartScan = {},
                onCancelScan = {},
                onOpenServerPicker = {},
                onCloseServerPicker = {},
                onChooseConnection = { _, _ -> },
                onOpenTautulli = {},
            ),
    )
}

/** The media-server page: the web client's group order for each kind, the scan's two states, and checked text rows. */
@RunWith(RobolectricTestRunner::class)
class MediaServerFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(
        draft: MediaServerForm,
        extras: MediaServerExtras = MediaServerExtras(),
    ) = rule.setContent { MediaServer(draft, extras) { saves++ } }

    private fun top(text: String) =
        rule
            .onNodeWithText(text, ignoreCase = true)
            .fetchSemanticsNode()
            .positionInRoot.y

    @Test
    fun `jellyfin leads with its libraries and keeps its settings last`() {
        show(JELLYFIN)

        assertTrue(top("Libraries") < top("Jellyfin settings"))
    }

    @Test
    fun `plex leads with its settings and ends with tautulli`() {
        show(MediaServerForm(kind = MediaServerKind.Plex, serverName = "Den", host = "plex.lan", port = "32400"))

        assertTrue(top("Plex settings") < top("Libraries"))
        rule.onNodeWithText("Den").assertExists()
        rule.onNodeWithText("Set up Tautulli").performScrollTo().assertExists()
    }

    @Test
    fun `a running scan shows its progress and a way to stop it`() {
        show(JELLYFIN, MediaServerExtras(scan = LibraryScan(running = true, progress = 1, total = 2, currentLibrary = null)))

        rule.onNodeWithText("Scanning… 1 of 2").assertExists()
        rule.onNodeWithText("Cancel the scan").assertExists()
    }

    @Test
    fun `a port out of range keeps done off`() {
        show(JELLYFIN)

        rule.onNode(hasText("Port") and hasClickAction()).performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("99999")

        rule.onNodeWithText("Enter a port between 1 and 65535.").assertExists()
        rule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun `the api key row shows only its last characters`() {
        show(JELLYFIN.copy(apiKey = "secret-key-1234"))

        rule.onNodeWithText("•••• 1234").performScrollTo().assertExists()
        rule.onNodeWithText("secret-key-1234").assertDoesNotExist()
    }

    @Test
    fun `the api key sheet does not display the key`() {
        show(JELLYFIN.copy(apiKey = "secret-key-1234"))

        rule.onNode(hasText("API key") and hasClickAction()).performScrollTo().performClick()

        // hasText also matches InputText, the raw value; what the field displays is EditableText.
        val displayed = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("•".repeat(15)))
        rule.onNode(displayed).assertExists()
        rule.onNodeWithText("Done").assertExists()
    }

    @Test
    fun `a blank host says it is required`() {
        show(JELLYFIN)

        rule.onNode(hasText("Host") and hasClickAction()).performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement(" ")

        rule.onNodeWithText("Required").assertExists()
        rule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun `a row's explanation stays in view while its value is edited`() {
        show(JELLYFIN.copy(externalUrl = "https://jf.example.com"))

        rule.onNode(hasText("External host") and hasClickAction()).performScrollTo().performClick()

        rule.onNode(hasSetTextAction() and hasText("https://jf.example.com")).assertExists()
        rule.onNodeWithText("Where users open the media server from their own devices; blank for the address above.").assertExists()
    }

    @Test
    fun `a clean change saves`() {
        show(JELLYFIN.copy(useSsl = true))

        rule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, saves)
    }
}

/** The media-server page on a television: OK on a text row opens its editor. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class MediaServerTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `ok on the host row opens its editor`() {
        rule.setContent { MediaServer(JELLYFIN) }
        rule.onNode(hasText("Host") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction() and hasText("jf.lan")).assertExists()
    }
}
