package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
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
    ) = rule.setContent { SeerrTheme { TitleStatusSheetContent(state, watchUrl, serverName, canRequest, onWatch) } }

    @Test
    fun `an available title offers Watch on the media server and opens its link`() {
        var opened: String? = null
        show(TitleState.Available, onWatch = { opened = it })

        rule.onNodeWithText("Watch on Plex").assertIsDisplayed().performClick()

        assertEquals("https://plex.example/watch", opened)
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
        show(TitleState.Processing)

        rule.onNodeWithText("Another user requested this title.").assertIsDisplayed()
    }

    @Test
    fun `a blocklisted title says it cannot be requested`() {
        show(TitleState.Blocklisted)

        rule.onNodeWithText("This title is blocklisted, so it can't be requested.").assertIsDisplayed()
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
