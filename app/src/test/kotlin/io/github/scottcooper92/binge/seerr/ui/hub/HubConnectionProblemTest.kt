package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
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
        state: HubUiState = HubUiState.Error(health),
        onReconnect: () -> Unit = {},
    ) = rule.setContent {
        SeerrTheme {
            HubScreen(
                state = state,
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
    fun `the allow button stays on open settings across a re-probe`() {
        var health by mutableStateOf(ConnectionHealth.LocalNetworkDenied)
        rule.setContent {
            SeerrTheme {
                HubScreen(
                    state = HubUiState.Error(health),
                    actions =
                        HubActions(
                            onOpenSection = {},
                            onOpenAccount = {},
                            onOpenRequest = {},
                            onRetry = {},
                            onReconnect = {},
                            onDisconnect = {},
                            onDismissBingeHint = {},
                        ),
                )
            }
        }

        rule.onNode(hasText(text(R.string.local_network_allow))).performClick()
        health = ConnectionHealth.Unreachable
        rule.waitForIdle()
        health = ConnectionHealth.LocalNetworkDenied
        rule.waitForIdle()

        rule.onNode(hasText(text(R.string.local_network_open_settings))).assertExists()
        rule.onNode(hasText(text(R.string.local_network_allow))).assertDoesNotExist()
    }

    /** The app goes to sign-in for a rejected session (#810), so the hub offers no way out of its own meanwhile. */
    @Test
    fun `a rejected session offers nothing of its own while the app goes to sign-in`() {
        show(
            ConnectionHealth.Unauthorized,
            state =
                HubUiState.Ready(
                    server = HubServer("https://seerr.test", "Seerr", SeerrVariant.Seerr, null, false, 0),
                    health = ConnectionHealth.Unauthorized,
                    overview = HubOverview(),
                    downloading = emptyList(),
                    bingeStatus = BingeStatus.NotInstalled,
                ),
        )

        rule.onNode(hasText(text(R.string.hub_retry))).assertDoesNotExist()
        rule.onNode(hasText(text(R.string.settings_edit_connection))).assertDoesNotExist()
    }
}
