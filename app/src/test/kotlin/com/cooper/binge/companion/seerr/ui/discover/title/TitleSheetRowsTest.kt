package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.cooper.binge.companion.seerr.ui.requests.SheetTitleRow
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitleSheetRowsTest {
    @get:Rule val rule: ComposeContentTestRule = createSeerrComposeRule()

    private fun rows(
        state: TitleState,
        url: String? = "https://plex.example/x",
        fourKOpen: Boolean = false,
        binge: (() -> Unit)? = null,
        remainingOpen: Boolean = false,
    ): List<String> {
        var labels = emptyList<String>()
        rule.setContent {
            labels =
                rememberTitleSheetRows(
                    TitleSheetFacts(state, url, "Plex", fourKOpen, remainingOpen),
                    {},
                    {},
                    onWatchInBinge = binge,
                ).labels()
        }
        rule.waitForIdle()
        return labels
    }

    private fun List<SheetTitleRow>.labels() = map { it.label }

    @Test
    fun `an available title offers Watch then Request in 4K`() {
        assertEquals(listOf("Watch on Plex", "Request in 4K"), rows(TitleState.Available, fourKOpen = true))
    }

    @Test
    fun `a title not in the library offers no Watch even with a link`() {
        assertEquals(listOf("Request in 4K"), rows(TitleState.Requested, fourKOpen = true))
    }

    @Test
    fun `Watch needs a link and Request in 4K needs the permission`() {
        assertEquals(emptyList<String>(), rows(TitleState.Available, url = null))
    }

    @Test
    fun `where Binge answers Watch in Binge comes before the media server`() {
        assertEquals(listOf("Watch in Binge", "Watch on Plex", "Request in 4K"), rows(TitleState.Available, fourKOpen = true, binge = {}))
    }

    @Test
    fun `Watch in Binge needs the title in the library`() {
        assertEquals(emptyList<String>(), rows(TitleState.Requested, binge = {}))
    }

    @Test
    fun `Watch in Binge needs the link Watch needs`() {
        assertEquals(emptyList<String>(), rows(TitleState.Available, url = null, binge = {}))
    }

    @Test
    fun `a partly available title with seasons left offers the remaining seasons between Watch and Request in 4K`() {
        assertEquals(
            listOf("Watch on Plex", "Request the remaining seasons", "Request in 4K"),
            rows(TitleState.PartiallyAvailable, fourKOpen = true, remainingOpen = true),
        )
    }

    @Test
    fun `the remaining seasons are offered only for a title in the library`() {
        assertEquals(emptyList<String>(), rows(TitleState.Processing, url = null, remainingOpen = true))
    }
}
