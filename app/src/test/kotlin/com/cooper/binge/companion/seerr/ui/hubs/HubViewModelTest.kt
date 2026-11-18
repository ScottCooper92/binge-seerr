package com.cooper.binge.companion.seerr.ui.hubs

import com.cooper.binge.companion.seerr.discover.GenreTile
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.discover.sliders.DiscoverSliderRepository
import com.cooper.binge.companion.seerr.discover.sliders.HubCardLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.SliderListState
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSlider
import com.cooper.binge.companion.seerr.ui.settings.server.SliderForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    ) : TitleListRepository {
        override suspend fun supports(source: TitleSource) = true

        override suspend fun load(
            source: TitleSource,
            page: Int,
        ): TitlePage {
            if (fail) error("down")
            val movie = TitleResult(TitleKind.Movie, 1, "One", "2020", null, null, null, null, TitleAvailability.None, null)
            return TitlePage(listOf(movie), 1, 1)
        }

        override suspend fun genres(kind: TitleKind): List<GenreTile> = emptyList()
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

    private fun vm(
        sliders: FakeSliders,
        titles: TitleListRepository = Titles(),
    ) = HubViewModel(sliders, HubCardLoader(titles, NoMedia), dispatcher, HubTab.Movies)

    private suspend fun HubViewModel.settled() = uiState.first { it !is DiscoverHubUiState.Loading }

    @Test
    fun `the hub shows the rows of the servers sliders that have titles`() =
        runTest {
            val sliders = FakeSliders(SliderListState.Ready(listOf(slider(1, TRENDING), slider(2, TRENDING, enabled = false))))
            val state = vm(sliders).settled()
            val ready = state as DiscoverHubUiState.Ready
            assertEquals(listOf(1), ready.rows.map { it.row.sliderId })
            assertEquals(
                "One",
                ready.rows
                    .single()
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
}

private const val TRENDING = 4
private const val WATCHLIST = 3
