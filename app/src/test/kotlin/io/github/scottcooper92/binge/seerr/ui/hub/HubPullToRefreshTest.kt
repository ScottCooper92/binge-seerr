package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The dashboard pulls; a problem page does not, since its own retry re-checks the server as a whole. */
@RunWith(RobolectricTestRunner::class)
class HubPullToRefreshTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var refreshes = 0

    private fun show(state: HubUiState) =
        rule.setContent {
            SeerrTheme {
                HubScreen(
                    state = state,
                    actions =
                        HubActions(
                            onOpenSection = {},
                            onOpenAccount = {},
                            onOpenRequest = {},
                            onRetry = {},
                            onReconnect = {},
                            onDisconnect = {},
                            onDismissBingeHint = {},
                            onRefresh = { refreshes++ },
                        ),
                )
            }
        }

    private fun pull() {
        rule.onRoot().performTouchInput { swipeDown(startY = top + height / 4f, endY = bottom) }
        rule.waitForIdle()
    }

    private val dashboard = readyHub(HubOverview(loaded = true, permissions = SeerrPermissions(isAdmin = true)))

    @Test
    fun `a pull on the dashboard refreshes once`() {
        show(dashboard)

        pull()

        assertEquals(1, refreshes)
    }

    @Test
    fun `a pull while the dashboard refreshes starts no second`() {
        show(dashboard.copy(refreshing = true))

        pull()

        assertEquals(0, refreshes)
    }

    @Test
    fun `a problem page does not pull`() {
        show(dashboard.copy(health = ConnectionHealth.Unreachable))

        pull()

        assertEquals(0, refreshes)
    }
}
