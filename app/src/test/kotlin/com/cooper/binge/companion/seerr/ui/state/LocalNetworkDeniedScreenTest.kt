package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.ui.test.hasText
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.core.ui.R as UiR
import com.cooper.homelab.core.uiandroid.R as CoreR

/**
 * A screen's failure for a server Android will not let this app reach reads as the hub does: the same words and Allow,
 * with no Try again, which could not work (#1507).
 */
@RunWith(RobolectricTestRunner::class)
class LocalNetworkDeniedScreenTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a screen's failure offers to allow the local network, not to try again`() {
        rule.setContent { SeerrTheme { ErrorScreen(error = SeerrError.LocalNetworkDenied, onRetry = {}) } }

        rule.onNode(hasText(string(UiR.string.hub_local_network_headline))).assertExists()
        rule.onNode(hasText(string(UiR.string.hub_local_network_body))).assertExists()
        rule.onNode(hasText(string(CoreR.string.connect_local_network_allow))).assertExists()
        rule.onNode(hasText(string(R.string.action_try_again))).assertDoesNotExist()
    }

    @Test
    fun `a failure that is only unreachable still offers to try again`() {
        rule.setContent { SeerrTheme { ErrorScreen(error = SeerrError.Unreachable, onRetry = {}) } }

        rule.onNode(hasText(string(CoreR.string.connect_local_network_allow))).assertDoesNotExist()
    }
}
