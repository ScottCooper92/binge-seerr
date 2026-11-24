package com.cooper.binge.companion.seerr.ui.discover.request

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val SERVER = RequestServer(1, "Radarr", is4k = false, isDefault = true, profileId = 4, rootFolder = "/media/movies")

private fun advanced() =
    RequestSheetUiState.Ready(
        target = RequestSheetTarget(TitleKind.Movie, tmdbId = 603, title = "The Matrix", meta = "1999"),
        access = RequestAccess(may4k = false, mayChooseDestination = true),
        quota = QuotaState.Known(null),
        servers = ServersState.Ready(listOf(SERVER)),
        pick =
            DestinationPick(
                serverId = 1,
                profileId = 4,
                rootFolder = "/media/movies",
                choices = ChoicesState.Ready(RequestChoices(listOf(RequestChoice(4, "HD-1080p")), listOf("/media/movies"))),
            ),
    )

/**
 * The sheet's page is saved state, so a fold or a rotation while a destination page is open keeps it open: the form's
 * Request button stays away, and the page's own row stays on screen.
 */
@RunWith(RobolectricTestRunner::class)
class RequestSheetPageRestoreTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun request() = string(R.string.request_sheet_submit)

    @Test
    fun `an open destination page is still open after the window is recreated`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestSheetContent(advanced(), RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        rule.onNodeWithText(request()).assertIsDisplayed()

        rule.onNodeWithText(string(R.string.request_server)).performClick()
        rule.onNodeWithText(request()).assertDoesNotExist()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(request()).assertDoesNotExist()
        rule.onNodeWithText("Radarr").assertIsDisplayed()
    }
}
