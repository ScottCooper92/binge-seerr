package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterCapabilities
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.grid.FilterLookups
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.grid.GridRequest
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import com.cooper.homelab.core.presentation.search.QUERY_DEBOUNCE_MS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        var failGenres = false

        override suspend fun canRefine(preset: GridPreset) = refinable

        override suspend fun genres(kind: TitleKind): List<FilterOption> {
            if (failGenres) throw IOException("down")
            return listOf(FilterOption("18", "Drama"), FilterOption("35", "Comedy"))
        }

        override suspend fun languages() = listOf(FilterOption("ja", "Japanese"))

        override suspend fun load(
            request: GridRequest,
            page: Int,
        ): TitlePage {
            requests += request to page
            return answer(request, page)
        }
    }

    private class FakeLookups : FilterLookups {
        var capabilities = FilterCapabilities(watch = true, certification = true, keywords = true, excludeKeywords = true, status = true)
        val providerRegions = mutableListOf<String>()
        val searches = mutableListOf<String>()
        var failProviders = false

        override suspend fun capabilities() = capabilities

        override suspend fun regions() = listOf(FilterOption("GB", "United Kingdom"), FilterOption("US", "United States"))

        override suspend fun defaultRegion() = "GB"

        override suspend fun providers(
            kind: TitleKind,
            region: String,
        ): List<FilterOption> {
            providerRegions += region
            if (failProviders) throw IOException("down")
            return listOf(FilterOption("8", "Netflix"))
        }

        override suspend fun certifications(kind: TitleKind) = mapOf("GB" to listOf("U", "PG", "15"))

        override suspend fun searchKeywords(query: String): List<FilterOption> {
            searches += query
            return listOf(FilterOption("1", "silent film"))
        }

        override suspend fun keywordNames(ids: Set<Int>) = ids.associateWith { "keyword $it" }
    }

    private val lookups = FakeLookups()

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
        val vm = DiscoverGridViewModel(grid, lookups, media, kind, preset, "Drama", "2026-11-19", null, null)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        try {
            advanceUntilIdle()
            block(vm)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `the user's own watchlist keeps both movies and series`() {
        grid.answer = { _, _ -> TitlePage(listOf(title(1), title(2, TitleKind.Series)), 1, 1) }
        runVm(preset = GridPreset.OwnWatchlist) { vm ->
            val state = vm.uiState.value as DiscoverGridUiState.Ready
            assertEquals(2, state.cards.size)
            assertTrue(state.cards.any { it.kind == TitleKind.Series })
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
                    lookups,
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
            assertTrue(DiscoverSort.RevenueDesc !in DiscoverSort.sheetChoices(TitleKind.Movie))
        }

    @Test
    fun `a list with no order or filter shows neither control`() {
        grid.refinable = false
        runVm(preset = GridPreset.Trending) { vm ->
            val controls = vm.uiState.value.controls
            assertNull(controls.sort)
            assertNull(controls.chip)
            assertNull(controls.sort)
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
    fun `an empty watchlist says so`() {
        grid.answer = { _, page -> TitlePage(emptyList(), page, 1) }
        runVm(preset = GridPreset.OwnWatchlist) { vm ->
            assertEquals(GridEmptyReason.Watchlist, (vm.uiState.value as DiscoverGridUiState.Empty).reason)
        }
    }

    @Test
    fun `filters the user added that match nothing are Filtered, and resetting them returns to the list's own`() {
        grid.answer = { _, page -> TitlePage(emptyList(), page, 1) }
        runVm { vm ->
            vm.onApply(DiscoverSort.PopularityDesc, DiscoverFilters(genres = setOf(18, 35), ratingMin = 8.0))
            advanceUntilIdle()
            val filtered = vm.uiState.value as DiscoverGridUiState.Empty
            assertEquals(GridEmptyReason.Filtered, filtered.reason)

            vm.onResetFilters()
            advanceUntilIdle()
            assertTrue((vm.uiState.value as DiscoverGridUiState.Empty).controls.groups.isEmpty())
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
    fun `retrying the grid also asks again for the filter lists that failed to load`() {
        grid.failGenres = true
        runVm { vm ->
            assertEquals(
                emptyList<String>(),
                vm.uiState.value.controls.vocabulary.genres
                    .map { it.name },
            )
            grid.failGenres = false
            vm.onRetry()
            advanceUntilIdle()
            assertEquals(
                listOf("Drama", "Comedy"),
                vm.uiState.value.controls.vocabulary.genres
                    .map { it.name },
            )
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

    @Test
    fun `the sheet offers the services of the user's region, the ratings and the keywords`() =
        runVm { vm ->
            val words = (vm.uiState.value as DiscoverGridUiState.Ready).controls.vocabulary
            assertTrue(words.capabilities.watch)
            assertEquals("GB", words.defaultRegion)
            assertEquals(listOf("8"), words.providers.getValue("GB").map { it.id })
            assertEquals(listOf("U", "PG", "15"), words.certifications.getValue("GB"))
            assertEquals(listOf("GB"), lookups.providerRegions)
        }

    @Test
    fun `a region's services are loaded once, when the sheet asks`() =
        runVm { vm ->
            vm.onProvidersNeeded("US")
            vm.onProvidersNeeded("US")
            advanceUntilIdle()
            assertEquals(listOf("GB", "US"), lookups.providerRegions)
        }

    @Test
    fun `a region whose services failed to load is asked for again`() =
        runVm { vm ->
            lookups.failProviders = true
            vm.onProvidersNeeded("US")
            advanceUntilIdle()
            val failed = (vm.uiState.value as DiscoverGridUiState.Ready).controls.vocabulary
            assertEquals("a failed read is a failure, not an empty region", setOf("US"), failed.providersFailed)
            assertFalse("US" in failed.providers)
            lookups.failProviders = false
            vm.onProvidersNeeded("US")
            advanceUntilIdle()
            assertEquals(emptySet<String>(), (vm.uiState.value as DiscoverGridUiState.Ready).controls.vocabulary.providersFailed)
            assertEquals(
                listOf("8"),
                (vm.uiState.value as DiscoverGridUiState.Ready)
                    .controls.vocabulary.providers
                    .getValue("US")
                    .map { it.id },
            )
        }

    @Test
    fun `typing a keyword searches once the typing settles and names what it found`() =
        runVm { vm ->
            vm.onKeywordQuery("sil")
            vm.onKeywordQuery("silent")
            advanceTimeBy(QUERY_DEBOUNCE_MS + 1)
            advanceUntilIdle()
            val words = (vm.uiState.value as DiscoverGridUiState.Ready).controls.vocabulary
            assertEquals(listOf("silent"), lookups.searches)
            assertEquals(listOf("silent film"), words.keywordResults?.map { it.name })
            assertEquals("silent film", words.keywordNames[1])
        }

    @Test
    fun `clearing the keyword field drops the results`() =
        runVm { vm ->
            vm.onKeywordQuery("silent")
            advanceTimeBy(QUERY_DEBOUNCE_MS + 1)
            advanceUntilIdle()
            vm.onKeywordQuery("")
            advanceUntilIdle()
            assertEquals(null, (vm.uiState.value as DiscoverGridUiState.Ready).controls.vocabulary.keywordResults)
        }

    @Test
    fun `keywords the grid was opened with are named`() =
        runTest {
            val vm = DiscoverGridViewModel(grid, lookups, media, TitleKind.Movie, GridPreset.Keyword(7), "x", "2026-11-19", null, null)
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
            val words = (vm.uiState.value as DiscoverGridUiState.Ready).controls.vocabulary
            assertEquals("keyword 7", words.keywordNames[7])
            collector.cancel()
        }

    @Test
    fun `a server without the filters leaves their rows out`() =
        runVm { vm ->
            lookups.capabilities = FilterCapabilities()
            val bare = DiscoverGridViewModel(grid, lookups, media, TitleKind.Movie, GridPreset.Genre(18), "Drama", "2026-11-19", null, null)
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { bare.uiState.collect {} }
            advanceUntilIdle()
            val words = (bare.uiState.value as DiscoverGridUiState.Ready).controls.vocabulary
            assertEquals(FilterCapabilities(), words.capabilities)
            assertTrue(words.regions.isEmpty() && words.certifications.isEmpty())
            collector.cancel()
        }

    @Test
    fun `a filter added on top of the row's own is a chip of its own and clearing it keeps the row's chip`() =
        runVm { vm ->
            vm.onApply(DiscoverSort.PopularityDesc, DiscoverFilters(genres = setOf(18), language = "ja"))
            advanceUntilIdle()
            val controls = (vm.uiState.value as DiscoverGridUiState.Ready).controls
            assertEquals(listOf(FilterGroup.Language), controls.groups)
            assertEquals("Drama", controls.chip?.label)
            vm.onClearGroup(FilterGroup.Language)
            advanceUntilIdle()
            val after = (vm.uiState.value as DiscoverGridUiState.Ready).controls
            assertEquals(emptyList<FilterGroup>(), after.groups)
            assertEquals(setOf(18), after.filters.genres)
        }

    @Test
    fun `a list that takes no filters has no filter chips`() {
        grid.refinable = false
        runVm { vm ->
            assertEquals(emptyList<FilterGroup>(), (vm.uiState.value as DiscoverGridUiState.Ready).controls.groups)
        }
    }

    @Test
    fun `a genre added to a genre row is a chip that names only it, and clearing it keeps the row's genre`() =
        runVm { vm ->
            vm.onApply(DiscoverSort.PopularityDesc, DiscoverFilters(genres = setOf(18, 35)))
            advanceUntilIdle()
            val controls = (vm.uiState.value as DiscoverGridUiState.Ready).controls
            assertEquals(setOf(35), controls.added.genres)
            assertEquals(listOf(FilterGroup.Genres), controls.groups)
            vm.onClearGroup(FilterGroup.Genres)
            advanceUntilIdle()
            val after = (vm.uiState.value as DiscoverGridUiState.Ready).controls
            assertEquals(setOf(18), after.filters.genres)
            assertEquals("Drama", after.chip?.label)
            assertEquals(emptyList<FilterGroup>(), after.groups)
        }

    @Test
    fun `keywords excluded on another kind's grid are named when the grid opens`() =
        runTest {
            val vm =
                DiscoverGridViewModel(
                    grid,
                    lookups,
                    media,
                    TitleKind.Series,
                    GridPreset.Popular,
                    null,
                    "2026-11-19",
                    DiscoverFilters(excludeKeywords = setOf(9)),
                    DiscoverSort.PopularityDesc,
                )
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals("keyword 9", vm.uiState.value.controls.vocabulary.keywordNames[9])
            collector.cancel()
        }

    @Test
    fun `clearing a service row's own chip keeps the service added and its region`() =
        runTest {
            val vm =
                DiscoverGridViewModel(
                    grid,
                    lookups,
                    media,
                    TitleKind.Movie,
                    GridPreset.Provider(8, "GB"),
                    "Netflix",
                    "2026-11-19",
                    null,
                    null,
                )
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
            vm.onApply(DiscoverSort.PopularityDesc, DiscoverFilters(providers = setOf(8, 337), region = "GB"))
            advanceUntilIdle()
            assertEquals("GB", vm.uiState.value.controls.added.region)
            vm.onClearFilter()
            advanceUntilIdle()
            val filters = vm.uiState.value.controls.filters
            assertEquals(setOf(337), filters.providers)
            assertEquals("GB", filters.region)
            collector.cancel()
        }
}
