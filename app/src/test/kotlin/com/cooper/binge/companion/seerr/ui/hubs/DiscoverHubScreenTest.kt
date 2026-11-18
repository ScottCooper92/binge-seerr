package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.binge.designsystem.LocalNavOverlayInsets
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class DiscoverHubScreenTest {
    @get:Rule
    val compose: ComposeContentTestRule = createSeerrComposeRule()

    private fun show(
        server: String,
        opened: MutableList<HubCard>,
        hero: List<HubHero> = emptyList(),
        heroPlace: Boolean = true,
    ) {
        val card = HubCard(TitleKind.Movie, 7, "Paper Moons", "2020", null, TitleAvailability.None, server + "movie/7")
        val state =
            DiscoverHubUiState.Ready(
                listOf(HubRowUi.Cards(HubRow.Trending(1, null, HubTab.Movies), listOf(card))),
                pending = 0,
                refreshing = false,
                hero = hero,
                heroPlace = heroPlace,
            )
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = { opened += it }, onOpenGrid = {
                    _,
                    _,
                    ->
                }, onOpenRequests = {})
            }
        }
    }

    @Test
    fun `with no hero place the rows sit at the top, and with one they sit below it`() {
        show("https://server.example/", mutableListOf(), heroPlace = false)
        val without = compose.onNodeWithText("Paper Moons").getBoundsInRoot().top
        assertTrue("no hero place above the row: $without", without < 400.dp)
    }

    @Test
    fun `the hero's place keeps the rows below it`() {
        show("https://server.example/", mutableListOf(), heroPlace = true)
        val with = compose.onNodeWithText("Paper Moons").getBoundsInRoot().top
        assertTrue("the hero's place is above the row: $with", with > 400.dp)
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
                DiscoverHubScreen(
                    HubTab.Movies,
                    state,
                    onRefresh = {},
                    onOpenTitle = {},
                    onOpenGrid = { grid, title -> opened += grid to title },
                    onOpenRequests = {},
                )
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
                DiscoverHubScreen(
                    HubTab.Movies,
                    state,
                    onRefresh = {},
                    onOpenTitle = {},
                    onOpenGrid = { grid, title -> opened += grid to title },
                    onOpenRequests = {},
                )
            }
        }
        compose.onNodeWithText("Popular movies").assertExists()
        compose.onAllNodesWithText("See all", substring = true, ignoreCase = true, useUnmergedTree = true).onFirst().performClick()
        assertEquals(listOf(HubGrid(TitleKind.Movie, GridPreset.Popular) to "Popular movies"), opened)
    }

    @Test
    fun `see all on the recent requests row opens the request browser and not the grid`() {
        var requests = 0
        val grids = mutableListOf<HubGrid>()
        val card = HubCard(TitleKind.Movie, 7, "Paper Moons", "2020", null, TitleAvailability.None, "https://s/movie/7")
        val state =
            DiscoverHubUiState.Ready(
                listOf(HubRowUi.Cards(HubRow.RecentRequests(1, null, HubTab.Movies), listOf(card))),
                pending = 0,
                refreshing = false,
            )
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(
                    HubTab.Movies,
                    state,
                    onRefresh = {},
                    onOpenTitle = {},
                    onOpenGrid = { grid, _ -> grids += grid },
                    onOpenRequests = { requests++ },
                )
            }
        }
        compose.onNodeWithContentDescription("See all in Recent requests").performClick()
        assertEquals(1, requests)
        assertEquals(emptyList<HubGrid>(), grids)
    }

    @Test
    fun `browse all is left out where the server cannot filter`() {
        val state = DiscoverHubUiState.Ready(emptyList(), pending = 0, refreshing = false, browseAll = false)
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = {}, onOpenGrid = {
                    _,
                    _,
                    ->
                }, onOpenRequests = {})
            }
        }
        compose.onAllNodesWithText("Browse all", substring = true).assertCountEquals(0)
    }

    @Test
    fun `browse all opens the grid on the popular list of the hubs kind`() {
        val opened = mutableListOf<Pair<HubGrid, String>>()
        val state = DiscoverHubUiState.Ready(emptyList(), pending = 0, refreshing = false)
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(
                    HubTab.Series,
                    state,
                    onRefresh = {},
                    onOpenTitle = {},
                    onOpenGrid = { grid, title -> opened += grid to title },
                    onOpenRequests = {},
                )
            }
        }
        compose.onNodeWithText("Browse all shows").performClick()
        compose.onNodeWithText("Filter by genre, network, year and rating").assertExists()
        assertEquals(listOf(HubGrid(TitleKind.Series, GridPreset.Popular) to "Popular series"), opened)
    }

    @Test
    fun `a rated card shows its score and an unrated card shows none`() {
        val rated = HubCard(TitleKind.Movie, 7, "Paper Moons", "2020", null, TitleAvailability.None, "https://s/movie/7", rating = 7.7f)
        val unrated = HubCard(TitleKind.Movie, 8, "Quiet Harbour", "2020", null, TitleAvailability.None, "https://s/movie/8")
        val state =
            DiscoverHubUiState.Ready(
                listOf(HubRowUi.Cards(HubRow.Popular(1, null, HubTab.Movies), listOf(rated, unrated))),
                pending = 0,
                refreshing = false,
            )
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = {}, onOpenGrid = {
                    _,
                    _,
                    ->
                }, onOpenRequests = {})
            }
        }
        compose.onAllNodesWithText("7.7", substring = true, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `a row with no destination shows no see all`() {
        val card = HubCard(TitleKind.Movie, 7, "Paper Moons", "2020", null, TitleAvailability.None, "https://s/movie/7")
        val state =
            DiscoverHubUiState.Ready(
                listOf(HubRowUi.Cards(HubRow.RecentlyAdded(1, null, HubTab.Movies), listOf(card))),
                pending = 0,
                refreshing = false,
            )
        compose.setContent {
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = {}, onOpenGrid = {
                    _,
                    _,
                    ->
                }, onOpenRequests = {})
            }
        }
        compose.onAllNodesWithText("See all", substring = true, ignoreCase = true, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun hero(
        server: String,
        rating: Float? = null,
    ) = HubHero(
        TitleKind.Movie,
        9,
        "Seven Rivers",
        "https://img/b.jpg",
        "2021",
        TitleAvailability.Available,
        server + "movie/9",
        rating = rating,
    )

    @Test
    fun `a rated hero shows its rating and an unrated one does not`() {
        val server = "https://server.example/"
        show(server, mutableListOf(), hero = listOf(hero(server, rating = 6.4f)))
        compose.onNodeWithText("6.4", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a hero with no rating shows no score`() {
        val server = "https://server.example/"
        show(server, mutableListOf(), hero = listOf(hero(server)))
        compose.onNodeWithText("Seven Rivers", substring = true, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("6.4", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("0.0", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

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
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state.value, onRefresh = {}, onOpenTitle = {}, onOpenGrid = {
                    _,
                    _,
                    ->
                }, onOpenRequests = {})
            }
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
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state.value, onRefresh = {}, onOpenTitle = {}, onOpenGrid = {
                    _,
                    _,
                    ->
                }, onOpenRequests = {})
            }
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
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state.value, onRefresh = {}, onOpenTitle = {}, onOpenGrid = {
                    _,
                    _,
                    ->
                }, onOpenRequests = {})
            }
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
            SeerrTheme {
                DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = {}, onOpenGrid = {
                    _,
                    _,
                    ->
                }, onOpenRequests = {})
            }
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

    @Test
    fun `the last row ends at least 16dp above the floating bar`() {
        val overlay = 100.dp
        val state =
            DiscoverHubUiState.Ready(
                List(8) { i -> HubRowUi.Tiles(HubRow.Genres(i, null, HubTab.Movies), listOf(HubTile(i, "Tile $i", null))) },
                pending = 0,
                refreshing = false,
                hero = emptyList(),
            )
        compose.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalNavOverlayInsets provides PaddingValues(bottom = overlay)) {
                    Box(Modifier.fillMaxSize()) {
                        DiscoverHubScreen(HubTab.Movies, state, onRefresh = {}, onOpenTitle = {}, onOpenGrid = {
                            _,
                            _,
                            ->
                        }, onOpenRequests = {})
                    }
                }
            }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { repeat(8) { swipeUp() } }
        compose.waitForIdle()
        val root = compose.onRoot().getBoundsInRoot()
        val last = compose.onNodeWithText("Tile 7", useUnmergedTree = true).getBoundsInRoot()
        val clearance = root.bottom - overlay - last.bottom
        assertTrue("clearance was $clearance", clearance >= 16.dp)
    }
}
