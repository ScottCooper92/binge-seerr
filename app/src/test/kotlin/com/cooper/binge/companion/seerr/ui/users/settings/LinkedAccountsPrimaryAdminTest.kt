package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The server always refuses to unlink the primary administrator, so the page offers no Unlink there and says why. */
@RunWith(RobolectricTestRunner::class)
class LinkedAccountsPrimaryAdminTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val note = "The primary administrator's Plex and media server sign-ins cannot be unlinked."

    private fun show(canUnlink: Boolean) =
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                LinkedAccountsScreen(
                    state =
                        LinkedAccountsUiState.Ready(
                            plex = LinkedAccount(UserOrigin.Plex, linked = false, linkedAs = null),
                            mediaServer = LinkedAccount(UserOrigin.Jellyfin, linked = true, linkedAs = "ana"),
                            canQuickConnect = false,
                            canUnlink = canUnlink,
                        ),
                    events = emptyFlow(),
                    actions =
                        LinkedAccountsActions(
                            onBack = {},
                            onRetry = {},
                            onLinkPlex = {},
                            onUnlinkPlex = {},
                            onLinkQuickConnect = {},
                            onLinkMediaServer = { _, _ -> },
                            onUnlinkMediaServer = {},
                            onPlexLaunched = {},
                            onCancelLink = {},
                        ),
                )
            }
        }

    @Test
    fun `the primary administrator's linked account has no Unlink and the page says why`() {
        show(canUnlink = false)

        rule.onAllNodesWithContentDescription("Unlink").assertCountEquals(0)
        rule.onAllNodesWithText(note).assertCountEquals(1)
    }

    @Test
    fun `anyone else's linked account keeps its Unlink and gets no note`() {
        show(canUnlink = true)

        rule.onAllNodesWithContentDescription("Unlink").assertCountEquals(1)
        rule.onAllNodesWithText(note).assertCountEquals(0)
    }

    @Test
    fun `an account that is not linked can still be linked by the primary administrator`() {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                LinkedAccountsScreen(
                    state =
                        LinkedAccountsUiState.Ready(
                            plex = LinkedAccount(UserOrigin.Plex, linked = false, linkedAs = null),
                            mediaServer = null,
                            canQuickConnect = false,
                            canUnlink = false,
                        ),
                    events = emptyFlow(),
                    actions = LinkedAccountsActions({}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}),
                )
            }
        }

        rule.onAllNodesWithContentDescription("Link").assertCountEquals(1)
        rule.onAllNodesWithText(note).assertCountEquals(0)
    }
}
