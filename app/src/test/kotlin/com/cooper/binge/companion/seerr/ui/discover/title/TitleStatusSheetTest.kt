package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sheet behind a primary, or Manage, with no request to manage: what more may be requested, otherwise a line saying why not. */
@RunWith(RobolectricTestRunner::class)
class TitleStatusSheetTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private fun show(
        state: TitleState,
        canRequest: Boolean = true,
        onUnblock: (() -> Unit)? = null,
        fourKOpen: Boolean = false,
        remainingOpen: Boolean = false,
        onRequestFourK: () -> Unit = {},
        onRequestRemaining: () -> Unit = {},
    ) = rule.setContent {
        SeerrTheme {
            TitleStatusSheetContent(
                state,
                canRequest,
                onUnblock = onUnblock,
                fourKOpen = fourKOpen,
                remainingOpen = remainingOpen,
                onRequestFourK = onRequestFourK,
                onRequestRemaining = onRequestRemaining,
            )
        }
    }

    @Test
    fun `an available title with 4K open offers Request in 4K and no Watch, which is the page's primary`() {
        var fourK = false
        show(TitleState.Available, fourKOpen = true, onRequestFourK = { fourK = true })

        rule.onNodeWithText("Watch", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Request in 4K").assertIsDisplayed().performClick()

        assertTrue(fourK)
    }

    @Test
    fun `a partly available series with seasons left offers the remaining seasons`() {
        var remaining = false
        show(TitleState.PartiallyAvailable, remainingOpen = true, onRequestRemaining = { remaining = true })

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
    fun `an available title with nothing more to request says it is in the library`() {
        show(TitleState.Available)

        rule.onNodeWithText("This title is in the library.").assertIsDisplayed()
    }

    @Test
    fun `someone else's request says so`() {
        show(TitleState.Requested)

        rule.onNodeWithText("Another user requested this title.").assertIsDisplayed()
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
