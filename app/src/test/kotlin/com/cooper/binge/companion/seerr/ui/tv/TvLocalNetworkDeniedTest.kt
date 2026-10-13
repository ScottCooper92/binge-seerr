package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.test.hasText
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A television page for a server Android will not let this app reach offers Allow, as the hub does, not a retry (#1507). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvLocalNetworkDeniedTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a detail page offers to allow the local network, not to try again`() {
        rule.setContent { BingeTvTheme { TvSeerrErrorPage(error = SeerrError.LocalNetworkDenied, onRetry = {}) } }

        rule.onNode(hasText(string(R.string.hub_local_network_headline))).assertExists()
        rule.onNode(hasText(string(R.string.local_network_allow_short))).assertExists()
        rule.onNode(hasText(string(R.string.hub_retry))).assertDoesNotExist()
    }

    @Test
    fun `a board with nothing to show says so the same way`() {
        rule.setContent {
            BingeTvTheme {
                TvRowsFallback(
                    rows = listOf(TvPagedRows<Int>(count = 0, at = { null }, refresh = TvLoadPhase.Denied)),
                    emptyBody = "Nothing yet",
                    onRetryLoad = {},
                )
            }
        }

        rule.onNode(hasText(string(R.string.local_network_allow_short))).assertExists()
        rule.onNode(hasText(string(R.string.tv_list_load_failed))).assertDoesNotExist()
    }
}
