package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
    onSave: (ServerUsersSettings) -> Unit,
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
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = { draft = it(draft) }, onSave = { onSave(draft) }),
        onOpenDefaultPermissions = onOpenDefaultPermissions,
    )
}

/** The server's Users page: the sign-in switches guard each other, and a limit's window shows only while it has one. */
@RunWith(RobolectricTestRunner::class)
class ServerUsersFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val saves = mutableListOf<ServerUsersSettings>()
    private var permissionsOpened = 0

    private fun show() = rule.setContent { Users(SAVED, onSave = { saves += it }, onOpenDefaultPermissions = { permissionsOpened++ }) }

    @Test
    fun `the sign-in switches are named for the media server`() {
        show()

        rule.onNodeWithText("Jellyfin sign-in").assertExists()
        rule.onNodeWithText("New Jellyfin users").assertExists()
    }

    @Test
    fun `turning every way in off says so and does not save`() {
        show()

        rule.onNode(hasText("Local sign-in") and hasClickAction()).performClick()
        rule.onNode(hasText("Jellyfin sign-in") and hasClickAction()).performClick()
        rule.onNodeWithText("Keep at least one way to sign in turned on.").assertExists()
        rule.onNodeWithText("Save").performClick()

        assertEquals(emptyList<ServerUsersSettings>(), saves)
    }

    @Test
    fun `a set limit shows its window, and unlimited hides it`() {
        show()
        rule.onNodeWithText("Every 14 days").assertExists()

        rule.onNode(hasText("Movie requests") and hasClickAction()).performScrollTo().performClick()
        rule.onNode(hasText("Unlimited") and hasClickAction()).performClick()

        rule.onNodeWithText("Every 14 days").assertDoesNotExist()
        // The scroll above tucked the top bar away, so Save is tapped by its action rather than at its off-screen centre.
        rule.onNodeWithText("Save").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(SAVED.copy(movieLimit = 0), saves.single())
    }

    @Test
    fun `the default permissions row opens their editor`() {
        show()

        rule.onNode(hasText("Default permissions") and hasClickAction()).performScrollTo().performClick()

        assertEquals(1, permissionsOpened)
    }
}
