package com.cooper.binge.companion.seerr.ui.hubs

import com.cooper.binge.companion.seerr.discover.GenreTile
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.grid.GridRequest
import com.cooper.binge.companion.seerr.discover.sliders.DiscoverSliderRepository
import com.cooper.binge.companion.seerr.discover.sliders.HubCardLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubHeroLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.HubTileLoader
import com.cooper.binge.companion.seerr.discover.sliders.SliderListState
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSlider
import com.cooper.binge.companion.seerr.ui.settings.server.SliderForm
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSliders(
        initial: SliderListState,
    ) : DiscoverSliderRepository {
        val state = MutableStateFlow(initial)
        var refreshes = 0
        override val sliders: StateFlow<SliderListState> = state

        override suspend fun refresh(): List<DiscoverSlider> {
            refreshes++
            return emptyList()
        }

        override suspend fun save(draft: List<DiscoverSlider>) = error("unused")

        override suspend fun add(form: SliderForm) = error("unused")

        override suspend fun update(
            id: Int,
            form: SliderForm,
        ) = error("unused")

        override suspend fun delete(id: Int) = error("unused")

        override suspend fun reset() = error("unused")
    }

    private class Titles(
        private val fail: Boolean = false,
        private val canDiscoverByCompany: Boolean = true,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : TitleListRepository {
        override suspend fun supports(source: TitleSource) = source !is TitleSource.ByCompany || canDiscoverByCompany

        override suspend fun load(
            source: TitleSource,
            page: Int,
        ): TitlePage {
            if (fail) error("down")
            gate?.await()
            val movie = TitleResult(TitleKind.Movie, 1, "One", "2020", null, "/b.jpg", null, null, TitleAvailability.None, null)
            return TitlePage(listOf(movie), 1, 1)
        }

        override suspend fun genres(kind: TitleKind): List<GenreTile> = listOf(GenreTile(28, "Action", "/g.jpg"))
    }

    private object NoMedia : HubMediaSource {
        override suspend fun serverUrl() = "https://s/"

        override suspend fun recentlyAdded() = emptyList<SeerrRequestMediaDto>()

        override suspend fun recentRequests() = emptyList<SeerrRequestMediaDto>()

        override suspend fun hydrate(
            mediaType: String,
            tmdbId: Int,
        ): HydratedTitle? = null
    }

    private fun slider(
        id: Int,
        typeCode: Int,
        enabled: Boolean = true,
    ) = DiscoverSlider(id, typeCode, null, true, enabled, null)

    private class FakeGrid(
        private val canRefine: Boolean = true,
        private val fail: Boolean = false,
        /** When set, the server answers only once this does. */
        private val answer: CompletableDeferred<Boolean>? = null,
    ) : DiscoverGridRepository {
        override suspend fun canRefine(preset: GridPreset): Boolean = if (fail) error("down") else answer?.await() ?: canRefine

        override suspend fun genres(kind: TitleKind): List<FilterOption> = emptyList()

        override suspend fun languages(): List<FilterOption> = emptyList()

        override suspend fun load(
            request: GridRequest,
            page: Int,
        ): TitlePage = error("unused")
    }

    private fun vm(
        sliders: FakeSliders,
        titles: TitleListRepository = Titles(),
        grid: DiscoverGridRepository = FakeGrid(),
    ) = HubViewModel(
        sliders,
        HubCardLoader(titles, NoMedia),
        HubTileLoader(titles),
        HubHeroLoader(titles, NoMedia),
        grid,
        dispatcher,
        HubTab.Movies,
    )

    private suspend fun HubViewModel.settled() = uiState.first { it !is DiscoverHubUiState.Loading }

    @Test
    fun `a genre row shows tiles with the genres backdrop`() =
        runTest {
            val ready = vm(FakeSliders(SliderListState.Ready(listOf(slider(1, MOVIE_GENRES))))).settled() as DiscoverHubUiState.Ready
            val tile = (ready.rows.single() as HubRowUi.Tiles).tiles.single()
            assertEquals("Action", tile.name)
            assertEquals(28, tile.id)
            assertTrue(tile.imageUrl.orEmpty().endsWith("/g.jpg"))
        }

    @Test
    fun `a studio row shows the fixed studios where the server can discover by company`() =
        runTest {
            val ready = vm(FakeSliders(SliderListState.Ready(listOf(slider(1, STUDIOS))))).settled() as DiscoverHubUiState.Ready
            assertTrue((ready.rows.single() as HubRowUi.Tiles).tiles.any { it.name == "Disney" })
        }

    @Test
    fun `a studio row is left out where the server cannot discover by company`() =
        runTest {
            val sliders = FakeSliders(SliderListState.Ready(listOf(slider(1, STUDIOS), slider(2, TRENDING))))
            val ready = vm(sliders, Titles(canDiscoverByCompany = false)).settled() as DiscoverHubUiState.Ready
            assertEquals(listOf(2), ready.rows.map { it.row.sliderId })
        }

    @Test
    fun `the hub shows the rows of the servers sliders that have titles`() =
        runTest {
            val sliders = FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING), slider(2, TRENDING, enabled = false))))
            val state = vm(sliders).settled()
            val ready = state as DiscoverHubUiState.Ready
            assertEquals(listOf(1), ready.rows.map { it.row.sliderId })
            assertEquals(
                "One",
                (ready.rows.single() as HubRowUi.Cards)
                    .cards
                    .single()
                    .name,
            )
        }

    @Test
    fun `it reads the slider list once on opening and again on a refresh`() =
        runTest {
            val sliders = FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING))))
            val model = vm(sliders)
            assertEquals(1, sliders.refreshes)
            model.refresh()
            assertEquals(2, sliders.refreshes)
        }

    @Test
    fun `a list that failed to load is an error`() =
        runTest {
            val sliders = FakeSliders(SliderListState.Failed(IllegalStateException("x")))
            assertTrue(vm(sliders).settled() is DiscoverHubUiState.Error)
        }

    @Test
    fun `every row failing is an error and no rows at all is empty`() =
        runTest {
            val failing = vm(FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING)))), Titles(fail = true))
            assertTrue(failing.settled() is DiscoverHubUiState.Error)
            val none = vm(FakeSliders(SliderListState.Ready(emptyList())))
            assertEquals(DiscoverHubUiState.Empty, none.settled())
        }

    @Test
    fun `a change to the list reaches an open hub`() =
        runTest {
            val sliders = FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING))))
            val model = vm(sliders)
            model.settled()
            sliders.state.value = SliderListState.Ready(listOf(slider(1, TRENDING), slider(3, WATCHLIST)))
            val ready = model.uiState.first { it is DiscoverHubUiState.Ready && it.rows.size == 2 } as DiscoverHubUiState.Ready
            assertEquals(listOf(1, 3), ready.rows.map { it.row.sliderId })
        }

    @Test
    fun `browse all shows where the server can filter and is left out where it cannot`() =
        runTest {
            val sliders = { FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING)))) }
            assertTrue((vm(sliders()).settled() as DiscoverHubUiState.Ready).browseAll)
            assertFalse((vm(sliders(), grid = FakeGrid(canRefine = false)).settled() as DiscoverHubUiState.Ready).browseAll)
        }

    @Test
    fun `a failed read of the servers filters leaves browse all showing`() =
        runTest {
            val ready = vm(FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING)))), grid = FakeGrid(fail = true)).settled()
            assertTrue((ready as DiscoverHubUiState.Ready).browseAll)
        }

    @Test
    fun `the hero rides with the rows and a failing hero never holds them`() =
        runTest {
            val ready = vm(FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING))))).settled() as DiscoverHubUiState.Ready
            assertEquals(listOf(1), ready.hero.map { it.tmdbId })
            val titles = Titles()
            val failingHero =
                HubViewModel(
                    FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING)))),
                    HubCardLoader(titles, NoMedia),
                    HubTileLoader(titles),
                    HubHeroLoader(Titles(fail = true), NoMedia),
                    FakeGrid(),
                    dispatcher,
                    HubTab.Movies,
                )
            val state = failingHero.uiState.first { it !is DiscoverHubUiState.Loading }
            assertTrue((state as DiscoverHubUiState.Ready).hero.isEmpty())
            assertEquals(listOf(1), state.rows.map { it.row.sliderId })
        }

    @Test
    fun `a cold launch stays a skeleton until the rows above a fast row land, and no row ever moves`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val titles = Titles(gate = gate)
            val sliders = FakeSliders(SliderListState.Loading)
            val model =
                HubViewModel(
                    sliders,
                    HubCardLoader(titles, NoMedia),
                    HubTileLoader(titles),
                    HubHeroLoader(titles, NoMedia),
                    FakeGrid(),
                    dispatcher,
                    HubTab.Movies,
                )
            val seen = mutableListOf<DiscoverHubUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { seen += it } }
            assertEquals("the slider list has not answered", DiscoverHubUiState.Loading, seen.last())

            sliders.state.value = SliderListState.Ready(listOf(slider(1, TRENDING), slider(2, STUDIOS)))
            assertEquals("the fast studios row is held behind the slow trending row", DiscoverHubUiState.Loading, seen.last())

            gate.complete(Unit)
            val ready = seen.last() as DiscoverHubUiState.Ready
            assertEquals(listOf(1, 2), ready.rows.map { it.row.sliderId })
            assertEquals(listOf(1), ready.hero.map { it.tmdbId })
            assertTrue(
                "no state showed the studios row on its own",
                seen.filterIsInstance<DiscoverHubUiState.Ready>().all {
                    it.rows
                        .first()
                        .row.sliderId ==
                        1
                },
            )
        }

    @Test
    fun `the hub waits for the answer on filtering, so Browse all is never shown and then taken away`() =
        runTest {
            val answer = CompletableDeferred<Boolean>()
            val model = vm(FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING)))), grid = FakeGrid(answer = answer))
            val seen = mutableListOf<DiscoverHubUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { seen += it } }
            testScheduler.advanceTimeBy(BROWSE_ALL_WAIT - 1)
            assertEquals("rows are ready but the answer is not", DiscoverHubUiState.Loading, seen.last())

            answer.complete(false)
            val ready = seen.last() as DiscoverHubUiState.Ready
            assertFalse(ready.browseAll)
            assertTrue("no state showed Browse all", seen.filterIsInstance<DiscoverHubUiState.Ready>().none { it.browseAll })
        }

    @Test
    fun `a server that does not answer in time is shown Browse all after the wait`() =
        runTest {
            val model = vm(FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING)))), grid = FakeGrid(answer = CompletableDeferred()))
            val seen = mutableListOf<DiscoverHubUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { seen += it } }
            testScheduler.advanceTimeBy(BROWSE_ALL_WAIT - 1)
            assertEquals(DiscoverHubUiState.Loading, seen.last())

            testScheduler.advanceTimeBy(2)
            assertTrue((seen.last() as DiscoverHubUiState.Ready).browseAll)
        }
}

private const val BROWSE_ALL_WAIT = 1_000L
private const val TRENDING = 4
private const val MOVIE_GENRES = 6
private const val STUDIOS = 8
private const val WATCHLIST = 3
