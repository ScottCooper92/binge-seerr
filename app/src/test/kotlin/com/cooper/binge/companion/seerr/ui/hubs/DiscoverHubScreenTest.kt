package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.HubTile
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
                listOf(HubRowUi.Cards(HubRow.Trending(1, null, HubTab.Movies), listOf(card))),
                pending = 0,
                refreshing = false,
                hero = hero,
            )
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = { opened += it }, onOpenGrid = { _, _ -> })
            }
        }
    }

    @Test
    fun `a tap on a card opens its title`() {
        val opened = mutableListOf<HubCard>()
        show("https://server.example/", opened)
        compose.onNodeWithText("Paper Moons").performClick()
        assertEquals(listOf(7), opened.map { it.tmdbId })
    }

    @Test
    fun `a tap on a genre or a studio tile opens the grid on that category`() {
        val opened = mutableListOf<Pair<HubGrid, String>>()
        val state =
            DiscoverHubUiState.Ready(
                listOf(
                    HubRowUi.Tiles(HubRow.Genres(1, null, HubTab.Movies), listOf(HubTile(28, "Action", null))),
                    HubRowUi.Tiles(HubRow.Studios(2, null), listOf(HubTile(2, "Disney", null))),
                ),
                pending = 0,
                refreshing = false,
            )
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = {}, onOpenGrid = { grid, title ->
                    opened +=
                        grid to title
                })
            }
        }
        compose.onNodeWithText("Genres").assertExists()
        compose.onNodeWithText("Studios").assertExists()
        compose.onNodeWithText("Action", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Disney", useUnmergedTree = true).performClick()
        assertEquals(
            listOf(
                HubGrid(TitleKind.Movie, GridPreset.Genre(28)) to "Action",
                HubGrid(TitleKind.Movie, GridPreset.Company(2)) to "Disney",
            ),
            opened,
        )
    }

    @Test
    fun `see all on a poster row opens the grid on that list`() {
        val opened = mutableListOf<Pair<HubGrid, String>>()
        val card = HubCard(TitleKind.Movie, 7, "Paper Moons", "2020", null, TitleAvailability.None, "https://s/movie/7")
        val state =
            DiscoverHubUiState.Ready(
                listOf(HubRowUi.Cards(HubRow.Popular(1, null, HubTab.Movies), listOf(card))),
                pending = 0,
                refreshing = false,
            )
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = {}, onOpenGrid = { grid, title ->
                    opened +=
                        grid to title
                })
            }
        }
        compose.onNodeWithText("Popular movies").assertExists()
        compose.onAllNodesWithText("See all", substring = true, ignoreCase = true, useUnmergedTree = true).onFirst().performClick()
        assertEquals(listOf(HubGrid(TitleKind.Movie, GridPreset.Popular) to "Popular movies"), opened)
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
                HubRowUi.Cards(HubRow.Trending(id, null, HubTab.Movies), listOf(card))
            }
        val state = mutableStateOf(DiscoverHubUiState.Ready(rows, pending = 0, refreshing = false))
        compose.setContent {
            SeerrTheme { DiscoverHubScreen(HubTab.Movies, state.value, onRefresh = {}, onOpenTitle = {}, onOpenGrid = { _, _ -> }) }
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
                HubRowUi.Cards(HubRow.Trending(id, null, HubTab.Movies), listOf(card))
            }
        val state = mutableStateOf(DiscoverHubUiState.Ready(rows, pending = 0, refreshing = false))
        compose.setContent {
            SeerrTheme { DiscoverHubScreen(HubTab.Movies, state.value, onRefresh = {}, onOpenTitle = {}, onOpenGrid = { _, _ -> }) }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("Title 1").assertDoesNotExist()

        state.value = state.value.copy(hero = listOf(hero(server)))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Seven Rivers", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Title 1").assertDoesNotExist()
    }

    @Test
    fun `a pull down at the top of the hub does not stop a late hero from showing`() {
        val server = "https://server.example/"
        val rows =
            (1..4).map { id ->
                val card = HubCard(TitleKind.Movie, id, "Title $id", "2020", null, TitleAvailability.None, server + "movie/$id")
                HubRowUi.Cards(HubRow.Trending(id, null, HubTab.Movies), listOf(card))
            }
        val state = mutableStateOf(DiscoverHubUiState.Ready(rows, pending = 0, refreshing = false))
        compose.setContent {
            SeerrTheme { DiscoverHubScreen(HubTab.Movies, state.value, onRefresh = {}, onOpenTitle = {}, onOpenGrid = { _, _ -> }) }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithText("Title 1").assertIsDisplayed()

        state.value = state.value.copy(hero = listOf(hero(server)))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Seven Rivers", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a restored hub that already has its hero keeps the place it was scrolled to`() {
        val server = "https://server.example/"
        val rows =
            (1..12).map { id ->
                val card = HubCard(TitleKind.Movie, id, "Title $id", "2020", null, TitleAvailability.None, server + "movie/$id")
                HubRowUi.Cards(HubRow.Trending(id, null, HubTab.Movies), listOf(card))
            }
        val state = DiscoverHubUiState.Ready(rows, pending = 0, refreshing = false, hero = listOf(hero(server)))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            SeerrTheme { DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = {}, onOpenGrid = { _, _ -> }) }
        }
        repeat(2) { compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Seven Rivers", substring = true).assertDoesNotExist()
        val shownBefore =
            compose.onAllNodesWithText("Title", substring = true).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.Text].toString()
            }

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Seven Rivers", substring = true).assertDoesNotExist()
        val shownAfter =
            compose.onAllNodesWithText("Title", substring = true).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.Text].toString()
            }
        assertEquals(shownBefore, shownAfter)
    }
}
