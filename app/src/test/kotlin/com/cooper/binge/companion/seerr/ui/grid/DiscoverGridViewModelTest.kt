package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.grid.GridRequest
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

private fun title(
    id: Int,
    kind: TitleKind = TitleKind.Movie,
) = TitleResult(kind, id, "Title $id", "2020", "/p$id.jpg", null, null, null, TitleAvailability.None, null)

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverGridViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeGrid(
        var refinable: Boolean = true,
        var answer: (GridRequest, Int) -> TitlePage = { _, page -> TitlePage(listOf(title(page), title(page + 10)), page, 2) },
    ) : DiscoverGridRepository {
        val requests = mutableListOf<Pair<GridRequest, Int>>()

        override suspend fun canRefine(preset: GridPreset) = refinable

        override suspend fun genres(kind: TitleKind) = listOf(FilterOption("18", "Drama"), FilterOption("35", "Comedy"))

        override suspend fun languages() = listOf(FilterOption("ja", "Japanese"))

        override suspend fun load(
            request: GridRequest,
            page: Int,
        ): TitlePage {
            requests += request to page
            return answer(request, page)
        }
    }

    private val media =
        object : HubMediaSource {
            override suspend fun serverUrl() = "https://server.example/"

            override suspend fun recentlyAdded() = emptyList<SeerrRequestMediaDto>()

            override suspend fun recentRequests() = emptyList<SeerrRequestMediaDto>()

            override suspend fun hydrate(
                mediaType: String,
                tmdbId: Int,
            ): HydratedTitle? = null
        }
    private val grid = FakeGrid()

    private fun runVm(
        preset: GridPreset = GridPreset.Genre(18),
        kind: TitleKind = TitleKind.Movie,
        block: suspend TestScope.(DiscoverGridViewModel) -> Unit,
    ) = runTest {
        val vm = DiscoverGridViewModel(grid, media, kind, preset, "Drama", "2026-11-19", null, null)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        try {
            advanceUntilIdle()
            block(vm)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `it opens on the row's filter and the first page of it`() =
        runVm { vm ->
            val state = vm.uiState.value as DiscoverGridUiState.Ready
            assertEquals(listOf(1, 11), state.cards.map { it.tmdbId })
            assertTrue(state.hasMore)
            val (request, page) = grid.requests.single()
            assertEquals(1, page)
            assertEquals(DiscoverFilters(genres = setOf(18)), request.filters)
            assertEquals(DiscoverSort.PopularityDesc, request.sort)
            assertEquals(ActiveFilterChip(ChipKind.Genre, "Drama"), state.controls.chip)
            assertEquals(DiscoverSort.PopularityDesc, state.controls.sort)
            assertEquals(DiscoverSort.forKind(TitleKind.Movie), state.controls.sortChoices)
        }

    @Test
    fun `clearing the chip shows the whole list from its first page`() =
        runVm { vm ->
            vm.onLoadMore()
            advanceUntilIdle()
            vm.onClearFilter()
            advanceUntilIdle()
            val state = vm.uiState.value as DiscoverGridUiState.Ready
            assertNull(state.controls.chip)
            assertEquals(listOf(1, 11), state.cards.map { it.tmdbId })
            val last = grid.requests.last()
            assertEquals(DiscoverFilters(), last.first.filters)
            assertEquals(1, last.second)
        }

    @Test
    fun `a new order starts the list again and keeps the filter`() =
        runVm { vm ->
            vm.onApply(DiscoverSort.RatingDesc, vm.uiState.value.controls.filters)
            advanceUntilIdle()
            val last = grid.requests.last()
            assertEquals(DiscoverSort.RatingDesc, last.first.sort)
            assertEquals(setOf(18), last.first.filters.genres)
            assertEquals(1, last.second)
            assertEquals(DiscoverSort.RatingDesc, vm.uiState.value.controls.sort)
        }

    @Test
    fun `the sheet's pickers are the genres and languages the server offers`() =
        runVm { vm ->
            advanceUntilIdle()
            val words = vm.uiState.value.controls.vocabulary
            assertEquals(listOf("Drama", "Comedy"), words.genres.map { it.name })
            assertEquals(listOf("ja"), words.languages.map { it.id })
        }

    @Test
    fun `a grid reopened by a kind switch starts on the filters and order it was given`() =
        runTest {
            val given = DiscoverFilters(runtimeMin = 90)
            val vm =
                DiscoverGridViewModel(
                    grid,
                    media,
                    TitleKind.Series,
                    GridPreset.Popular,
                    null,
                    "2026-11-19",
                    given,
                    DiscoverSort.FirstAirNewest,
                )
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
            val (request, _) = grid.requests.first()
            assertEquals(given, request.filters)
            assertEquals(DiscoverSort.FirstAirNewest, request.sort)
            collector.cancel()
        }

    @Test
    fun `the sheet opens on the filters in force and applying them unchanged keeps them and the chip`() =
        runVm { vm ->
            val controls = vm.uiState.value.controls
            assertEquals(setOf(18), controls.filters.genres)
            vm.onApply(controls.sort ?: DiscoverSort.PopularityDesc, controls.filters)
            advanceUntilIdle()
            assertEquals(
                setOf(18),
                grid.requests
                    .last()
                    .first.filters.genres,
            )
            assertEquals(
                ChipKind.Genre,
                vm.uiState.value.controls.chip
                    ?.kind,
            )
        }

    @Test
    fun `a filter added in the sheet keeps the row's chip and clearing the chip keeps the added filter`() =
        runVm { vm ->
            val controls = vm.uiState.value.controls
            vm.onApply(DiscoverSort.PopularityDesc, controls.filters.copy(runtimeMin = 90))
            advanceUntilIdle()
            assertEquals(
                ChipKind.Genre,
                vm.uiState.value.controls.chip
                    ?.kind,
            )
            vm.onClearFilter()
            advanceUntilIdle()
            val filters =
                grid.requests
                    .last()
                    .first.filters
            assertTrue(filters.genres.isEmpty())
            assertEquals(90, filters.runtimeMin)
        }

    @Test
    fun `upcoming starts from the day it was given and soonest first`() =
        runVm(preset = GridPreset.Upcoming) { vm ->
            val (request, _) = grid.requests.single()
            assertEquals("2026-11-19", request.filters.releaseFrom)
            assertEquals(DiscoverSort.ReleaseOldest, request.sort)
            assertEquals(ActiveFilterChip(ChipKind.Upcoming, null), vm.uiState.value.controls.chip)
        }

    @Test
    fun `a show's upcoming list starts on its first air date`() =
        runVm(preset = GridPreset.Upcoming, kind = TitleKind.Series) { vm ->
            grid.answer = { _, page -> TitlePage(listOf(title(page, TitleKind.Series)), page, 1) }
            vm.onRetry()
            advanceUntilIdle()
            grid.requests.clear()
            vm.onRetry()
            advanceUntilIdle()
            assertEquals(
                DiscoverSort.FirstAirOldest,
                grid.requests
                    .single()
                    .first.sort,
            )
            assertTrue(DiscoverSort.RevenueDesc !in vm.uiState.value.controls.sortChoices)
        }

    @Test
    fun `a list with no order or filter shows neither control`() {
        grid.refinable = false
        runVm(preset = GridPreset.Trending) { vm ->
            val controls = vm.uiState.value.controls
            assertNull(controls.sort)
            assertNull(controls.chip)
            assertTrue(controls.sortChoices.isEmpty())
        }
    }

    @Test
    fun `a title repeated on a further page is shown once and a kind the grid is not of is left out`() {
        grid.answer = { _, page ->
            if (page == 1) {
                TitlePage(listOf(title(1), title(2), title(9, TitleKind.Person)), 1, 2)
            } else {
                TitlePage(listOf(title(2), title(3)), 2, 2)
            }
        }
        runVm { vm ->
            vm.onLoadMore()
            advanceUntilIdle()
            val state = vm.uiState.value as DiscoverGridUiState.Ready
            assertEquals(listOf(1, 2, 3), state.cards.map { it.tmdbId })
        }
    }

    @Test
    fun `an empty page is empty and keeps the chip so it can be cleared`() {
        grid.answer = { _, page -> TitlePage(emptyList(), page, 1) }
        runVm { vm ->
            val state = vm.uiState.value
            assertTrue(state is DiscoverGridUiState.Empty)
            assertEquals(ActiveFilterChip(ChipKind.Genre, "Drama"), state.controls.chip)
        }
    }

    @Test
    fun `a failure is worded by the server error and can be retried`() {
        grid.answer = { _, _ -> throw IOException("down") }
        runVm { vm ->
            assertEquals(SeerrError.Unreachable, (vm.uiState.value as DiscoverGridUiState.Error).error)
            grid.answer = { _, page -> TitlePage(listOf(title(page)), page, 1) }
            vm.onRetry()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is DiscoverGridUiState.Ready)
        }
    }

    @Test
    fun `opening a card sends it with the server's page`() =
        runVm { vm ->
            val event = awaitEvent(vm.events)
            val card = (vm.uiState.value as DiscoverGridUiState.Ready).cards.first()
            vm.onOpenTitle(card)
            advanceUntilIdle()
            val opened = (event.await() as DiscoverGridEvent.Open).card
            assertEquals("https://server.example/movie/1", opened.webUrl)
        }

    @Test
    fun `a page of another kind does not stop the paging`() =
        runVm { vm ->
            grid.answer = { _, page ->
                if (page == 2) TitlePage(listOf(title(page, TitleKind.Series)), page, 3) else TitlePage(listOf(title(page)), page, 3)
            }
            vm.onRetry()
            advanceUntilIdle()
            vm.onLoadMore()
            advanceUntilIdle()
            val state = vm.uiState.value as DiscoverGridUiState.Ready
            assertEquals(listOf(1, 3), state.cards.map { it.tmdbId })
        }

    @Test
    fun `a page of titles already shown does not stop paging`() {
        grid.answer = { _, page ->
            when (page) {
                1 -> TitlePage(listOf(title(1), title(2)), 1, 3)
                2 -> TitlePage(listOf(title(1), title(2)), 2, 3)
                else -> TitlePage(listOf(title(3)), 3, 3)
            }
        }
        runVm { vm ->
            vm.onLoadMore()
            advanceUntilIdle()
            val state = vm.uiState.value as DiscoverGridUiState.Ready
            assertEquals(listOf(1, 2, 3), state.cards.map { it.tmdbId })
            assertTrue(!state.hasMore)
        }
    }
}
