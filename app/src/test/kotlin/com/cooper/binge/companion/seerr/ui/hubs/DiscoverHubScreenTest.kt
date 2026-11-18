package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscoverHubScreenTest {
    @get:Rule
    val compose: ComposeContentTestRule = createSeerrComposeRule()

    private fun show(
        server: String,
        opened: MutableList<HubCard>,
    ) {
        val card = HubCard(TitleKind.Movie, 7, "Paper Moons", "2020", null, TitleAvailability.None, server + "movie/7")
        val state =
            DiscoverHubUiState.Ready(
                listOf(HubRowUi(HubRow.Trending(1, null, HubTab.Movies), listOf(card))),
                pending = 0,
                refreshing = false,
            )
        compose.setContent {
            SeerrTheme { DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = { opened += it }) }
        }
    }

    @Test
    fun `a tap on a card opens its title on a real server`() {
        val opened = mutableListOf<HubCard>()
        show("https://server.example/", opened)
        compose.onNodeWithText("Paper Moons").performClick()
        assertEquals(listOf(7), opened.map { it.tmdbId })
    }
}
