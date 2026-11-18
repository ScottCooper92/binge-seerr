package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
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
        hero: List<HubHero> = emptyList(),
    ) {
        val card = HubCard(TitleKind.Movie, 7, "Paper Moons", "2020", null, TitleAvailability.None, server + "movie/7")
        val state =
            DiscoverHubUiState.Ready(
                listOf(HubRowUi(HubRow.Trending(1, null, HubTab.Movies), listOf(card))),
                pending = 0,
                refreshing = false,
                hero = hero,
            )
        compose.setContent {
            SeerrTheme { DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = { opened += it }) }
        }
    }

    @Test
    fun `a tap on a card opens its title`() {
        val opened = mutableListOf<HubCard>()
        show("https://server.example/", opened)
        compose.onNodeWithText("Paper Moons").performClick()
        assertEquals(listOf(7), opened.map { it.tmdbId })
    }

    private fun hero(server: String) =
        HubHero(TitleKind.Movie, 9, "Seven Rivers", "https://img/b.jpg", "2021", 7.5f, TitleAvailability.Available, server + "movie/9")

    @Test
    fun `the hero shows above the rows and a tap on it opens its title`() {
        val opened = mutableListOf<HubCard>()
        show("https://server.example/", opened, hero = listOf(hero("https://server.example/")))
        compose.onNodeWithContentDescription("Seven Rivers", substring = true).assertExists().performClick()
        assertEquals(listOf(9), opened.map { it.tmdbId })
    }

    @Test
    fun `the hub has no title bar and names itself to a screen reader`() {
        show("https://server.example/", mutableListOf())
        compose.onNodeWithText("Movies").assertDoesNotExist()
        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Movies"))
            .assertExists()
    }

    @Test
    fun `a hero that arrives after the rows shows at the top of a hub nobody has scrolled`() {
        val server = "https://server.example/"
        val rows =
            (1..4).map { id ->
                val card = HubCard(TitleKind.Movie, id, "Title $id", "2020", null, TitleAvailability.None, server + "movie/$id")
                HubRowUi(HubRow.Trending(id, null, HubTab.Movies), listOf(card))
            }
        val state = mutableStateOf(DiscoverHubUiState.Ready(rows, pending = 0, refreshing = false))
        compose.setContent {
            SeerrTheme { DiscoverHubScreen(HubTab.Movies, state.value, onRefresh = {}, onOpenTitle = {}) }
        }
        compose.onNodeWithText("Title 1").assertIsDisplayed()

        state.value = state.value.copy(hero = listOf(hero(server)))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Seven Rivers", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a hero that arrives after the user has scrolled does not pull the hub back to the top`() {
        val server = "https://server.example/"
        val rows =
            (1..12).map { id ->
                val card = HubCard(TitleKind.Movie, id, "Title $id", "2020", null, TitleAvailability.None, server + "movie/$id")
                HubRowUi(HubRow.Trending(id, null, HubTab.Movies), listOf(card))
            }
        val state = mutableStateOf(DiscoverHubUiState.Ready(rows, pending = 0, refreshing = false))
        compose.setContent {
            SeerrTheme { DiscoverHubScreen(HubTab.Movies, state.value, onRefresh = {}, onOpenTitle = {}) }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("Title 1").assertDoesNotExist()

        state.value = state.value.copy(hero = listOf(hero(server)))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Seven Rivers", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Title 1").assertDoesNotExist()
    }
}
