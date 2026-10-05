package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** An address that no longer answers needs a way to change it without disconnecting first (#649). */
@RunWith(RobolectricTestRunner::class)
class HubConnectionProblemTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun show(
        health: ConnectionHealth,
        onReconnect: () -> Unit = {},
    ) = rule.setContent {
        SeerrTheme {
            HubScreen(
                state = HubUiState.Error(health),
                actions =
                    HubActions(
                        onOpenSection = {},
                        onOpenAccount = {},
                        onOpenRequest = {},
                        onRetry = {},
                        onReconnect = onReconnect,
                        onDisconnect = {},
                        onDismissBingeHint = {},
                    ),
            )
        }
    }

    @Test
    fun `an unreachable server offers to edit the connection`() {
        var reconnects = 0
        show(ConnectionHealth.Unreachable) { reconnects++ }

        rule.onNode(hasText(text(R.string.settings_edit_connection))).performClick()

        assertEquals(1, reconnects)
    }

    @Test
    fun `a dashboard that could not load offers to edit the connection`() {
        show(ConnectionHealth.CouldNotLoad)

        rule.onNode(hasText(text(R.string.settings_edit_connection))).assertExists()
    }

    @Test
    fun `a refused local network offers to allow it instead of a retry, and still lets the connection be edited`() {
        var reconnects = 0
        show(ConnectionHealth.LocalNetworkDenied) { reconnects++ }

        rule.onNode(hasText(text(R.string.hub_local_network_headline))).assertExists()
        rule.onNode(hasText(text(R.string.local_network_allow))).assertExists()
        rule.onNode(hasText(text(R.string.hub_retry))).assertDoesNotExist()
        rule.onNode(hasText(text(R.string.settings_edit_connection))).performClick()

        assertEquals(1, reconnects)
    }

    @Test
    fun `a rejected session keeps its single sign in again action`() {
        show(ConnectionHealth.Unauthorized)

        rule.onNode(hasText(text(R.string.hub_sign_in_again))).assertExists()
        rule.onNode(hasText(text(R.string.settings_edit_connection))).assertDoesNotExist()
    }
}
