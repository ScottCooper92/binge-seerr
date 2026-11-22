package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealth
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The hub's problem page for a server whose certificate the device refuses: no retry, since the same address is refused again. */
@RunWith(RobolectricTestRunner::class)
class HubCertificateRefusedTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var reconnects = 0

    private fun show() =
        rule.setContent {
            SeerrTheme {
                HubScreen(
                    state = HubUiState.Error(ConnectionHealth.CertificateRefused),
                    actions =
                        HubActions(
                            onOpenSection = {},
                            onOpenAccount = {},
                            onOpenRequest = {},
                            onRetry = {},
                            onReconnect = { reconnects++ },
                            onDisconnect = {},
                            onDismissBingeHint = {},
                            onRefresh = {},
                        ),
                )
            }
        }

    @Test
    fun `the connection's reading becomes the hub's own problem`() {
        assertEquals(ConnectionHealth.CertificateRefused, SeerrConnectionHealth.CertificateRefused.toConnectionHealth())
    }

    @Test
    fun `it is a problem the hub shows in place of its dashboard`() {
        assertTrue(ConnectionHealth.CertificateRefused.isProblem())
        assertFalse(ConnectionHealth.CertificateRefused.showsDashboard())
    }

    @Test
    fun `the page says the certificate is refused and offers no try again`() {
        show()

        rule.onNode(hasText(string(R.string.hub_certificate_refused_headline))).assertExists()
        rule.onNode(hasText(string(R.string.hub_certificate_refused_body))).assertExists()
        rule.onNode(hasText(string(R.string.action_try_again))).assertDoesNotExist()
    }

    @Test
    fun `it still lets the connection be edited and the user sign out`() {
        show()

        rule.onNode(hasText(string(R.string.hub_disconnect))).assertExists()
        rule.onNode(hasText(string(R.string.settings_edit_connection))).performClick()

        assertEquals(1, reconnects)
    }
}
