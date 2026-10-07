package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.ui.test.hasText
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.ui.hub.HubOverview
import io.github.scottcooper92.binge.seerr.ui.hub.USER_COUNT
import io.github.scottcooper92.binge.seerr.ui.hub.readyHub
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The board's Users tile, gated as the phone's stat is: only a viewer who manages users is told the count (#788). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvHubUsersTileTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show(permissions: SeerrPermissions) =
        rule.setContent {
            BingeTvTheme {
                TvHubBoard(
                    state = readyHub(HubOverview(loaded = true, permissions = permissions, userCount = USER_COUNT)),
                    actions = TvHubActions({}, {}, {}, {}, {}, {}),
                )
            }
        }

    @Test
    fun `a viewer who manages users sees the tile`() {
        show(SeerrPermissions(canManageUsers = true))

        rule.onNode(hasText("$USER_COUNT")).assertExists()
    }

    @Test
    fun `a viewer who cannot manage users sees no Users tile`() {
        show(SeerrPermissions(canRequest = true))

        rule.onNode(hasText("$USER_COUNT")).assertDoesNotExist()
    }
}
