package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.hub.BingeStatus
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubOverview
import io.github.scottcooper92.binge.seerr.ui.hub.HubServer
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The problem page holds through a re-probe's Checking, rather than blinking to loading and dropping the remote (#796). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvHubProblemHoldTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var health by mutableStateOf(ConnectionHealth.Unreachable)

    @Test
    fun `a re-probe keeps the problem it was showing, and the next answer replaces it`() {
        rule.setContent { BingeTvTheme { TvHubBoard(state = ready(health), actions = TvHubActions({}, {}, {})) } }
        rule.onNode(hasText(string(R.string.hub_unreachable_headline))).assertExists()

        health = ConnectionHealth.Checking
        rule.waitForIdle()
        rule.onNode(hasText(string(R.string.hub_unreachable_headline))).assertExists()

        health = ConnectionHealth.CouldNotLoad
        rule.waitForIdle()
        rule.onNode(hasText(string(R.string.hub_couldnt_load_headline))).assertExists()
    }

    private fun ready(health: ConnectionHealth) =
        HubUiState.Ready(
            server = HubServer("https://seerr.test", "Seerr", SeerrVariant.Seerr, null, false, 0),
            health = health,
            overview = HubOverview(loaded = true),
            downloading = emptyList(),
            bingeStatus = BingeStatus.Connected,
        )

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}
