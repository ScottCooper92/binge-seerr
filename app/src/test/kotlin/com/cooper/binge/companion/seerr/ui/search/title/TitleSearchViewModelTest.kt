package com.cooper.binge.companion.seerr.ui.search.title

import com.cooper.binge.companion.seerr.core.search.QUERY_DEBOUNCE_MS
import com.cooper.binge.companion.seerr.core.search.QueryHistory
import com.cooper.binge.companion.seerr.core.search.SearchPage
import com.cooper.binge.companion.seerr.discover.GenreTile
import com.cooper.binge.companion.seerr.discover.StudioResult
import com.cooper.binge.companion.seerr.discover.StudioSearch
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
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

private fun title(
    id: Int,
    kind: TitleKind = TitleKind.Movie,
) = TitleResult(kind, id, "Title $id", "2020", "/p$id.jpg", null, null, null, TitleAvailability.None, null)

@OptIn(ExperimentalCoroutinesApi::class)
class TitleSearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeTitles(
        var answer: (TitleSource.Search, Int) -> TitlePage,
    ) : TitleListRepository {
        val loads = mutableListOf<Pair<String, Int>>()
        var trending: () -> TitlePage = { TitlePage(listOf(title(101), title(102, TitleKind.Series), title(103, TitleKind.Person)), 1, 1) }

        override suspend fun supports(source: TitleSource) = true

        override suspend fun load(
            source: TitleSource,
            page: Int,
        ): TitlePage {
            if (source is TitleSource.Trending) return trending()
            source as TitleSource.Search
            loads += source.query to page
            return answer(source, page)
        }

        override suspend fun genres(kind: TitleKind): List<GenreTile> = emptyList()

        override suspend fun genreNames(kind: TitleKind): Map<Int, String> =
            if (kind == TitleKind.Movie) mapOf(28 to "Action") else mapOf(18 to "Drama")
    }

    private class FakeHistory(
        initial: List<String> = emptyList(),
    ) : QueryHistory {
        val list = MutableStateFlow(initial)
        override val recents = list

        override suspend fun remember(query: String) {
            list.value = listOf(query) + list.value.filter { it != query }
        }

        override suspend fun clear() {
            list.value = emptyList()
        }
    }

    private class FakeGrid(
        var canRefine: Boolean = true,
    ) : DiscoverGridRepository {
        override suspend fun canRefine(preset: GridPreset): Boolean = canRefine

        override suspend fun genres(kind: TitleKind): List<FilterOption> = emptyList()

        override suspend fun languages(): List<FilterOption> = emptyList()

        override suspend fun load(
            request: GridRequest,
            page: Int,
        ): TitlePage = error("unused")
    }

    private val grid = FakeGrid()
    private val history = FakeHistory(listOf("alien"))
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
    private var studioPage: (String, Int) -> SearchPage<StudioResult> = { _, page -> SearchPage(emptyList(), page, 1) }
    private var studiosSupported = true
    private val studios =
        object : StudioSearch {
            override suspend fun supported() = studiosSupported

            override suspend fun search(
                query: String,
                page: Int,
            ) = studioPage(query, page)
        }
    private val titles = FakeTitles { source, page -> TitlePage(listOf(title(page), title(page + 10, TitleKind.Person)), page, 2) }

    /** Runs [block] with the view model's state being collected, on the test's scheduler, and stops the collector after. */
    private fun runVm(block: suspend TestScope.(TitleSearchViewModel) -> Unit) =
        runTest {
            val vm = TitleSearchViewModel(titles, studios, history, media, grid)
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            try {
                block(vm)
            } finally {
                collector.cancel()
            }
        }

    @Test
    fun `it starts idle with the recents`() =
        runVm { vm ->
            advanceUntilIdle()
            val idle = vm.uiState.value as TitleSearchUiState.Idle
            assertEquals(listOf("alien"), idle.recents)
            assertEquals("", idle.text)
        }

    @Test
    fun `the trending rails are the first page split into movies and series`() =
        runVm { vm ->
            advanceUntilIdle()
            val rails = (vm.uiState.value as TitleSearchUiState.Idle).trending as Trending.Ready
            assertEquals(listOf(101), rails.movies.map { it.tmdbId })
            assertEquals(listOf(102), rails.series.map { it.tmdbId })
        }

    @Test
    fun `a trending failure leaves the rails out and clearing the field asks again`() =
        runVm { vm ->
            titles.trending = { throw IOException("down") }
            val failing = TitleSearchViewModel(titles, studios, history, media, grid)
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { failing.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(Trending.Unavailable, (failing.uiState.value as TitleSearchUiState.Idle).trending)
            titles.trending = { TitlePage(listOf(title(101)), 1, 1) }
            failing.onClear()
            advanceUntilIdle()
            assertTrue((failing.uiState.value as TitleSearchUiState.Idle).trending is Trending.Ready)
            collector.cancel()
        }

    @Test
    fun `the type chips count what has loaded and All counts every row, collections too, whatever total the server gave`() =
        runVm { vm ->
            titles.answer = { _, page ->
                TitlePage(
                    listOf(
                        title(page),
                        title(page + 10, TitleKind.Series),
                        title(page + 20, TitleKind.Person),
                        title(page + 30, TitleKind.Collection),
                    ),
                    page,
                    5,
                    total = 50,
                )
            }
            vm.onQueryChange("matrix")
            advanceUntilIdle()
            val ready = vm.uiState.value as TitleSearchUiState.Ready
            assertEquals(4, ready.counts[SearchType.All])
            assertEquals(1, ready.counts[SearchType.Movies])
            assertEquals(1, ready.counts[SearchType.Series])
            assertEquals(1, ready.counts[SearchType.People])
        }

    @Test
    fun `choosing a type narrows the list and a new query starts on All again`() =
        runVm { vm ->
            vm.onQueryChange("matrix")
            advanceUntilIdle()
            vm.onType(SearchType.People)
            assertEquals(listOf(11), (vm.uiState.value as TitleSearchUiState.Ready).results.map { it.tmdbId })
            assertEquals(SearchType.People, (vm.uiState.value as TitleSearchUiState.Ready).type)
            vm.onQueryChange("alien")
            advanceUntilIdle()
            assertEquals(SearchType.All, (vm.uiState.value as TitleSearchUiState.Ready).type)
        }

    @Test
    fun `a type absent from the loaded pages leaves an empty list and the loaded count still grows`() =
        runVm { vm ->
            titles.answer = { _, page -> TitlePage(listOf(title(page), title(page + 10, TitleKind.Series)), page, 3) }
            vm.onQueryChange("matrix")
            advanceUntilIdle()
            vm.onType(SearchType.People)
            val first = vm.uiState.value as TitleSearchUiState.Ready
            assertTrue(first.results.isEmpty())
            assertTrue(first.hasMore)
            vm.onLoadMore()
            advanceUntilIdle()
            val second = vm.uiState.value as TitleSearchUiState.Ready
            assertTrue(second.loaded > first.loaded)
        }

    @Test
    fun `the genre names of both kinds reach the results`() =
        runVm { vm ->
            vm.onQueryChange("matrix")
            advanceUntilIdle()
            assertEquals(mapOf(28 to "Action", 18 to "Drama"), (vm.uiState.value as TitleSearchUiState.Ready).genreNames)
        }

    @Test
    fun `typing shows the text at once and the results after the debounce`() =
        runVm { vm ->
            vm.onQueryChange("matrix")
            assertEquals("matrix", vm.uiState.value.text)
            advanceTimeBy(QUERY_DEBOUNCE_MS + 1)
            advanceUntilIdle()
            val ready = vm.uiState.value as TitleSearchUiState.Ready
            assertEquals(listOf(1, 11), ready.results.map { it.tmdbId })
            assertTrue(ready.hasMore)
            assertEquals(listOf("matrix" to 1), titles.loads)
        }

    @Test
    fun `a recent search fills the field and runs at once`() =
        runVm { vm ->
            vm.onRecent("alien")
            advanceUntilIdle()
            assertEquals("alien", vm.uiState.value.text)
            assertTrue(vm.uiState.value is TitleSearchUiState.Ready)
            assertEquals(listOf("alien" to 1), titles.loads)
        }

    @Test
    fun `an empty page is no results for the query`() =
        runVm { vm ->
            titles.answer = { _, page -> TitlePage(emptyList(), page, 1) }
            vm.onQueryChange("zzzz")
            advanceUntilIdle()
            assertEquals(TitleSearchUiState.NoResults("zzzz", "zzzz", SearchType.All), vm.uiState.value)
        }

    @Test
    fun `a server that cannot filter the grid has no browse all after no results`() =
        runVm { vm ->
            grid.canRefine = false
            val fresh = TitleSearchViewModel(titles, studios, history, media, grid)
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { fresh.uiState.collect {} }
            titles.answer = { _, page -> TitlePage(emptyList(), page, 1) }
            fresh.onQueryChange("zzzz")
            advanceUntilIdle()
            assertEquals(TitleSearchUiState.NoResults("zzzz", "zzzz", SearchType.All, browseAll = false), fresh.uiState.value)
            collector.cancel()
        }

    @Test
    fun `a failure is worded by the server error and retried`() =
        runVm { vm ->
            titles.answer = { _, _ -> throw IOException("down") }
            vm.onQueryChange("matrix")
            advanceUntilIdle()
            assertEquals(SeerrError.Unreachable, (vm.uiState.value as TitleSearchUiState.Error).error)
            titles.answer = { _, page -> TitlePage(listOf(title(page)), page, 1) }
            vm.onRetry()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is TitleSearchUiState.Ready)
        }

    @Test
    fun `a further page appends`() =
        runVm { vm ->
            vm.onQueryChange("matrix")
            advanceUntilIdle()
            vm.onLoadMore()
            advanceUntilIdle()
            val ready = vm.uiState.value as TitleSearchUiState.Ready
            assertEquals(listOf(1, 11, 2, 12), ready.results.map { it.tmdbId })
        }

    @Test
    fun `a title repeated on a further page is shown once`() =
        runVm { vm ->
            titles.answer = { _, page -> TitlePage(listOf(title(1), title(page + 10, TitleKind.Person)), page, 2) }
            vm.onQueryChange("matrix")
            advanceUntilIdle()
            vm.onLoadMore()
            advanceUntilIdle()
            val ready = vm.uiState.value as TitleSearchUiState.Ready
            assertEquals(listOf(1, 11, 12), ready.results.map { it.tmdbId })
        }

    @Test
    fun `forgetting the recents empties them`() =
        runVm { vm ->
            advanceUntilIdle()
            vm.onForgetRecents()
            advanceUntilIdle()
            assertEquals(emptyList<String>(), (vm.uiState.value as TitleSearchUiState.Idle).recents)
        }

    @Test
    fun `opening a series sends its card with the server's page`() =
        runVm { vm ->
            val event = awaitEvent(vm.events)
            vm.onOpenTitle(title(5, TitleKind.Series))
            advanceUntilIdle()
            val card = (event.await() as TitleSearchEvent.Open).card
            assertEquals("https://server.example/tv/5", card.webUrl)
            assertEquals(5, card.tmdbId)
        }

    @Test
    fun `the Studios chip counts what the company search found and opens its list`() =
        runVm { vm ->
            studioPage = { _, page -> SearchPage(listOf(StudioResult(1, "Lucasfilm", null), StudioResult(2, "Pixar", null)), page, 1) }
            vm.onQueryChange("lucas")
            advanceUntilIdle()
            val all = vm.uiState.value as TitleSearchUiState.Ready
            assertEquals(2, all.counts[SearchType.Studios])
            assertEquals(all.results.size + 2, all.counts[SearchType.All])
            vm.onType(SearchType.Studios)
            val studiosList = vm.uiState.value as TitleSearchUiState.Ready
            assertEquals(listOf("Lucasfilm", "Pixar"), studiosList.studios.map { it.name })
            assertTrue(studiosList.results.isEmpty())
        }

    @Test
    fun `a query only studios answer still shows the chips`() =
        runVm { vm ->
            titles.answer = { _, page -> TitlePage(emptyList(), page, 1) }
            studioPage = { _, page -> SearchPage(listOf(StudioResult(1, "Lucasfilm", null)), page, 1) }
            vm.onQueryChange("lucas")
            advanceUntilIdle()
            val ready = vm.uiState.value as TitleSearchUiState.Ready
            assertEquals(1, ready.counts[SearchType.Studios])
            assertEquals(1, ready.counts[SearchType.All])
        }

    @Test
    fun `a server without company search leaves the Studios chip out`() =
        runVm { vm ->
            studiosSupported = false
            val noStudios = TitleSearchViewModel(titles, studios, history, media, grid)
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) { noStudios.uiState.collect {} }
            noStudios.onQueryChange("matrix")
            advanceUntilIdle()

            assertEquals(false, (noStudios.uiState.value as TitleSearchUiState.Ready).hasStudios)
            collector.cancel()
        }

    @Test
    fun `a failed studio search offers a retry on the Studios chip, not a spinner`() =
        runVm { vm ->
            studioPage = { _, _ -> throw IOException("down") }
            vm.onQueryChange("lucas")
            advanceUntilIdle()
            vm.onType(SearchType.Studios)

            val failed = vm.uiState.value as TitleSearchUiState.Ready
            assertTrue(failed.appendFailed)
            assertEquals(false, failed.hasMore)
            assertEquals(false, failed.appending)

            studioPage = { _, page -> SearchPage(listOf(StudioResult(1, "Lucasfilm", null)), page, 1) }
            vm.onLoadMore()
            advanceUntilIdle()
            assertEquals(listOf("Lucasfilm"), (vm.uiState.value as TitleSearchUiState.Ready).studios.map { it.name })
        }
}
