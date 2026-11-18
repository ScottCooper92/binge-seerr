package com.cooper.binge.companion.seerr.ui.search.title

import com.cooper.binge.companion.seerr.core.search.QUERY_DEBOUNCE_MS
import com.cooper.binge.companion.seerr.core.search.QueryHistory
import com.cooper.binge.companion.seerr.discover.GenreTile
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
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

        override suspend fun supports(source: TitleSource) = true

        override suspend fun load(
            source: TitleSource,
            page: Int,
        ): TitlePage {
            source as TitleSource.Search
            loads += source.query to page
            return answer(source, page)
        }

        override suspend fun genres(kind: TitleKind): List<GenreTile> = emptyList()
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

    private val history = FakeHistory(listOf("alien"))
    private val titles = FakeTitles { source, page -> TitlePage(listOf(title(page), title(page + 10, TitleKind.Person)), page, 2) }

    /** Runs [block] with the view model's state being collected, on the test's scheduler, and stops the collector after. */
    private fun runVm(block: suspend TestScope.(TitleSearchViewModel) -> Unit) =
        runTest {
            val vm = TitleSearchViewModel(titles, history)
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
            assertEquals(TitleSearchUiState.Idle("", listOf("alien")), vm.uiState.value)
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
            assertEquals(TitleSearchUiState.NoResults("zzzz", "zzzz"), vm.uiState.value)
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
    fun `forgetting the recents empties them`() =
        runVm { vm ->
            advanceUntilIdle()
            vm.onForgetRecents()
            advanceUntilIdle()
            assertEquals(TitleSearchUiState.Idle("", emptyList()), vm.uiState.value)
        }
}
