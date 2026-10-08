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

/**
 * The problem page stays through a re-probe, rather than blinking to loading and dropping the remote (#796). The view
 * model holds the problem and marks it rechecking (#873), so that is the state a re-probe reaches the board as, and the
 * board shows it working.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvHubProblemHoldTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var state by mutableStateOf(ready(ConnectionHealth.Unreachable))

    @Test
    fun `a re-probe keeps the problem it was showing, and the next answer replaces it`() {
        rule.setContent { BingeTvTheme { TvHubBoard(state = state, actions = TvHubActions({}, {}, {})) } }
        rule.onNode(hasText(string(R.string.hub_unreachable_headline))).assertExists()

        state = ready(ConnectionHealth.Unreachable).copy(rechecking = true)
        rule.waitForIdle()
        rule.onNode(hasText(string(R.string.hub_unreachable_headline))).assertExists()
        rule.onNode(hasText(string(R.string.hub_rechecking))).assertExists()

        state = ready(ConnectionHealth.CouldNotLoad)
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
