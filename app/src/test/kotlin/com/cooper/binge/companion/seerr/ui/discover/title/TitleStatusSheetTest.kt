package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sheet behind a primary with no request to manage: Watch once the title is in the library, otherwise a line saying why not. */
@RunWith(RobolectricTestRunner::class)
class TitleStatusSheetTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private fun show(
        state: TitleState,
        watchUrl: String? = "https://plex.example/watch",
        serverName: String? = "Plex",
        canRequest: Boolean = true,
        onWatch: (String) -> Unit = {},
        onUnblock: (() -> Unit)? = null,
        onWatchInBinge: (() -> Unit)? = null,
        fourKOpen: Boolean = false,
        remainingOpen: Boolean = false,
        onRequestFourK: () -> Unit = {},
        onRequestRemaining: () -> Unit = {},
    ) = rule.setContent {
        SeerrTheme {
            TitleStatusSheetContent(
                state,
                watchUrl,
                serverName,
                canRequest,
                onWatch,
                onWatchInBinge = onWatchInBinge,
                onUnblock = onUnblock,
                fourKOpen = fourKOpen,
                remainingOpen = remainingOpen,
                onRequestFourK = onRequestFourK,
                onRequestRemaining = onRequestRemaining,
            )
        }
    }

    @Test
    fun `an available title offers Watch on the media server and opens its link`() {
        var opened: String? = null
        show(TitleState.Available, onWatch = { opened = it })

        rule.onNodeWithText("Watch on Plex").assertIsDisplayed().performClick()

        assertEquals("https://plex.example/watch", opened)
    }

    @Test
    fun `where Binge answers the sheet offers Watch in Binge above the media server`() {
        var opened = false
        var server: String? = null
        show(TitleState.Available, onWatch = { server = it }, onWatchInBinge = { opened = true })

        rule.onNodeWithText("Watch on Plex").assertIsDisplayed()
        rule.onNodeWithText("Watch in Binge").assertIsDisplayed().performClick()

        assertTrue(opened)
        assertEquals(null, server)
    }

    @Test
    fun `where Binge does not answer there is no Watch in Binge`() {
        show(TitleState.Available)

        rule.onNodeWithText("Watch in Binge").assertDoesNotExist()
    }

    @Test
    fun `an available title with 4K open offers Watch and Request in 4K, and the second opens the request`() {
        var fourK = false
        show(TitleState.Available, fourKOpen = true, onRequestFourK = { fourK = true })

        rule.onNodeWithText("Watch on Plex").assertIsDisplayed()
        rule.onNodeWithText("Request in 4K").assertIsDisplayed().performClick()

        assertTrue(fourK)
    }

    @Test
    fun `a partly available series with seasons left offers Watch and the remaining seasons`() {
        var remaining = false
        show(TitleState.PartiallyAvailable, remainingOpen = true, onRequestRemaining = { remaining = true })

        rule.onNodeWithText("Watch on Plex").assertIsDisplayed()
        rule.onNodeWithText("Request the remaining seasons").assertIsDisplayed().performClick()

        assertTrue(remaining)
    }

    @Test
    fun `a title with nothing more to request offers no request rows`() {
        show(TitleState.Available)

        rule.onNodeWithText("Request in 4K").assertDoesNotExist()
        rule.onNodeWithText("Request the remaining seasons").assertDoesNotExist()
    }

    @Test
    fun `a title not in the library offers no request rows even where 4K is open`() {
        show(TitleState.Requested, fourKOpen = true, remainingOpen = true)

        rule.onNodeWithText("Request in 4K").assertDoesNotExist()
        rule.onNodeWithText("Request the remaining seasons").assertDoesNotExist()
    }

    @Test
    fun `a media server with no name is just Watch`() {
        show(TitleState.PartiallyAvailable, serverName = null)

        rule.onNodeWithText("Watch").assertIsDisplayed()
    }

    @Test
    fun `an available title with no link says it is in the library`() {
        show(TitleState.Available, watchUrl = null)

        rule.onNodeWithText("This title is in the library.").assertIsDisplayed()
    }

    @Test
    fun `a title that is not in the library never offers Watch`() {
        show(TitleState.Requested)

        rule.onNodeWithText("Another user requested this title.").assertIsDisplayed()
        rule.onNodeWithText("Watch").assertDoesNotExist()
    }

    @Test
    fun `a processing title claims no requester`() {
        show(TitleState.Processing)

        rule.onNodeWithText("This title is on its way to the library.").assertIsDisplayed()
        rule.onNodeWithText("Another user requested this title.").assertDoesNotExist()
    }

    @Test
    fun `a blocklisted title offers Unblock to a viewer who may and runs it`() {
        var unblocked = false
        show(TitleState.Blocklisted, onUnblock = { unblocked = true })

        rule.onNodeWithText("Unblock").assertIsDisplayed().performClick()

        assertTrue(unblocked)
    }

    @Test
    fun `a blocklisted title offers no Unblock to a viewer who may not`() {
        show(TitleState.Blocklisted)

        rule.onNodeWithText("Unblock").assertDoesNotExist()
    }

    @Test
    fun `a title that is not blocklisted never offers Unblock`() {
        show(TitleState.Requested, onUnblock = {})

        rule.onNodeWithText("Unblock").assertDoesNotExist()
    }

    @Test
    fun `a blocklisted title says it cannot be requested`() {
        show(TitleState.Blocklisted)

        rule.onNodeWithText("This title is blocklisted, so it can't be requested.").assertIsDisplayed()
    }

    @Test
    fun `a declined title says so`() {
        show(TitleState.Declined, canRequest = false)

        rule.onNodeWithText("A request for this title was declined. Ask an admin if you want it reconsidered.").assertIsDisplayed()
    }

    @Test
    fun `a viewer who may request a show with no season to request yet is told there is nothing to request`() {
        show(TitleState.Request, canRequest = true)

        rule.onNodeWithText("There is nothing to request for this title yet.").assertIsDisplayed()
    }

    @Test
    fun `a viewer who may not request says so`() {
        show(TitleState.Request, canRequest = false)

        rule.onNodeWithText("Your account can't request this title.").assertIsDisplayed()
    }
}
