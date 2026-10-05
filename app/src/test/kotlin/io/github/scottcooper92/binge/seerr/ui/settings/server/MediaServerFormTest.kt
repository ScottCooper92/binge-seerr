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
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
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

/** The media-server page's sections: what starts closed, what a failed Save opens, and the required marks. */
@RunWith(RobolectricTestRunner::class)
class MediaServerFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(
        draft: MediaServerForm,
        extras: MediaServerExtras = MediaServerExtras(),
    ) = rule.setContent { MediaServer(draft, extras) { saves++ } }

    @Test
    fun `host and port are marked required and the links and scan start closed`() {
        show(JELLYFIN)

        rule.onNode(hasSetTextAction() and hasText("Host *", substring = true)).assertExists()
        rule.onNode(hasSetTextAction() and hasText("Port *", substring = true)).assertExists()
        rule.onNodeWithText("External host", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Start a full scan").assertDoesNotExist()
    }

    @Test
    fun `a running scan holds its section open`() {
        show(JELLYFIN, MediaServerExtras(scan = LibraryScan(running = true, progress = 1, total = 2, currentLibrary = null)))

        rule.onNodeWithText("Full scan").assertExists()
        rule.onNodeWithText("Start a full scan").assertDoesNotExist()
        rule.onNodeWithText("Cancel the scan", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun `a bad external host opens the links and save does not save`() {
        show(JELLYFIN.copy(externalUrl = "jf.example.com"))

        rule.onNode(hasSetTextAction() and hasText("jf.example.com")).assertExists()
        rule.onNodeWithText("Save").performClick()

        assertEquals(0, saves)
    }

    @Test
    fun `a clean change saves`() {
        show(JELLYFIN.copy(useSsl = true))

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }
}

/** The media-server page on a television: OK on the closed links header opens it, and down enters its first field. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class MediaServerTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `ok on the links header opens it and down enters its first field`() {
        rule.setContent { MediaServer(JELLYFIN) }
        rule.onNodeWithText("Links for users").performSemanticsAction(SemanticsActions.RequestFocus)

        press(Key.DirectionCenter)
        press(Key.DirectionDown)

        rule.onNode(hasSetTextAction() and hasText("External host", substring = true)).assertIsFocused()
    }
}
