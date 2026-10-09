package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val SAVED = ServerUsersSettings(localLogin = true, mediaServerLogin = true, movieLimit = 5, movieDays = 14, tvLimit = 2)

@Composable
private fun Users(
    initial: ServerUsersSettings,
    onEdit: (ServerUsersSettings) -> Unit,
    onOpenDefaultPermissions: () -> Unit,
) = BingeExpressiveTheme(dynamicColor = false) {
    var draft by remember { mutableStateOf(initial) }
    ServerUsersScreen(
        state =
            ExtrasEditorUiState.Ready(
                draft = draft,
                saved = SAVED,
                extras = ServerUsersExtras(mediaServer = SeerrMediaServer.Jellyfin),
            ),
        events = emptyFlow(),
        actions =
            EditorActions(onBack = {}, onRetry = {}, onEdit = {
                draft = it(draft)
                onEdit(draft)
            }, onSave = {}),
        onOpenDefaultPermissions = onOpenDefaultPermissions,
    )
}

/** The server's Users page: the sign-in switches guard each other, and a limit's window shows only while it has one. */
@RunWith(RobolectricTestRunner::class)
class ServerUsersFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val edits = mutableListOf<ServerUsersSettings>()
    private var permissionsOpened = 0

    private fun show() = rule.setContent { Users(SAVED, onEdit = { edits += it }, onOpenDefaultPermissions = { permissionsOpened++ }) }

    @Test
    fun `the sign-in switches are named for the media server`() {
        show()

        rule.onNodeWithText("Jellyfin sign-in").assertExists()
        rule.onNodeWithText("New Jellyfin users").assertExists()
    }

    @Test
    fun `turning every way in off says so, with no Save to press`() {
        show()

        rule.onNode(hasText("Local sign-in") and hasClickAction()).performClick()
        rule.onNode(hasText("Jellyfin sign-in") and hasClickAction()).performClick()
        rule.onNodeWithText("Keep at least one way to sign in turned on.").assertExists()
        // The page saves as it changes, so there is no Save; the view model never writes a draft that fails this check.
        rule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun `a set limit shows its window, and unlimited hides it`() {
        show()
        rule.onNodeWithText("Every 14 days").assertExists()

        rule.onNode(hasText("Movie requests") and hasClickAction()).performScrollTo().performClick()
        // The shared choice sheet's long list is lazy: a row is composed once scrolled to.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Unlimited"))
        rule.onNode(hasText("Unlimited") and hasClickAction()).performClick()

        rule.onNodeWithText("Every 14 days").assertDoesNotExist()
        // The pick is the edit that saves.
        assertEquals(SAVED.copy(movieLimit = 0), edits.last())
    }

    @Test
    fun `the default permissions row opens their editor`() {
        show()

        rule.onNode(hasText("Default permissions") and hasClickAction()).performScrollTo().performClick()

        assertEquals(1, permissionsOpened)
    }
}
