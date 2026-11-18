package com.cooper.binge.companion.seerr.core.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PagedQuerySearchTest {
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

    private val calls = mutableListOf<Pair<String, Int>>()
    private val jobs = mutableListOf<Job>()

    /** A scope on the test's scheduler that [advanceUntilIdle] counts, which `backgroundScope` is not; ended by [end]. */
    private fun TestScope.scope(): CoroutineScope = CoroutineScope(coroutineContext.minusKey(Job) + Job().also(jobs::add))

    @After
    fun end() = jobs.forEach(Job::cancel)

    private fun newSearch(
        scope: CoroutineScope,
        history: QueryHistory = FakeHistory(),
        keyOf: ((String) -> Any)? = null,
        fetch: suspend (String, Int) -> SearchPage<String> = { q, p -> SearchPage(listOf("$q-$p"), p, 3) },
    ) = PagedQuerySearch(scope, history, { q, p ->
        calls += q to p
        fetch(q, p)
    }, keyOf = keyOf)

    @Test
    fun `starts idle with the recents and searches nothing`() =
        runTest {
            val search = newSearch(scope(), FakeHistory(listOf("alien")))
            scope().launch { search.state.collect {} }
            advanceUntilIdle()
            assertEquals(QuerySearchState.Idle(listOf("alien")), search.state.value)
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `a query shorter than the minimum is idle`() =
        runTest {
            val search = newSearch(scope())
            scope().launch { search.state.collect {} }
            search.setQuery("a")
            advanceUntilIdle()
            assertTrue(search.state.value is QuerySearchState.Idle)
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `typing is debounced into one search`() =
        runTest {
            val search = newSearch(scope())
            scope().launch { search.state.collect {} }
            search.setQuery("al")
            advanceTimeBy(QUERY_DEBOUNCE_MS - 1)
            search.setQuery("ali")
            advanceTimeBy(QUERY_DEBOUNCE_MS - 1)
            assertTrue(calls.isEmpty())
            advanceUntilIdle()
            assertEquals(listOf("ali" to 1), calls)
            assertEquals("ali", search.query.value)
            assertEquals(QuerySearchState.Results("ali", listOf("ali-1"), hasMore = true), search.state.value)
        }

    @Test
    fun `the query is trimmed`() =
        runTest {
            val search = newSearch(scope())
            scope().launch { search.state.collect {} }
            search.setQuery("  alien ")
            advanceUntilIdle()
            assertEquals(listOf("alien" to 1), calls)
            assertEquals("  alien ", search.query.value)
        }

    @Test
    fun `submit searches without waiting`() =
        runTest {
            val search = newSearch(scope())
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            search.submit()
            advanceTimeBy(1)
            assertEquals(listOf("alien" to 1), calls)
            advanceUntilIdle()
            assertEquals(1, calls.size)
        }

    @Test
    fun `a query that changes mid-load drops the first result`() =
        runTest {
            val slow = CompletableDeferred<SearchPage<String>>()
            val search =
                newSearch(scope()) { q, p -> if (q == "alien") slow.await() else SearchPage(listOf("$q-$p"), p, 1) }
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            advanceTimeBy(QUERY_DEBOUNCE_MS + 1)
            assertEquals(QuerySearchState.Loading("alien"), search.state.value)
            search.setQuery("aliens")
            advanceUntilIdle()
            slow.complete(SearchPage(listOf("late"), 1, 1))
            advanceUntilIdle()
            assertEquals(QuerySearchState.Results("aliens", listOf("aliens-1"), hasMore = false), search.state.value)
        }

    @Test
    fun `no items is no results and is not remembered`() =
        runTest {
            val history = FakeHistory()
            val search = newSearch(scope(), history) { _, p -> SearchPage(emptyList(), p, 1) }
            scope().launch { search.state.collect {} }
            search.setQuery("zzzz")
            advanceUntilIdle()
            assertEquals(QuerySearchState.NoResults("zzzz"), search.state.value)
            assertTrue(history.list.value.isEmpty())
        }

    @Test
    fun `a query with results is remembered`() =
        runTest {
            val history = FakeHistory()
            val search = newSearch(scope(), history)
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            advanceUntilIdle()
            assertEquals(listOf("alien"), history.list.value)
        }

    @Test
    fun `a failure can be retried for the same query`() =
        runTest {
            var fail = true
            val search =
                newSearch(scope()) { q, p -> if (fail) throw IOException("down") else SearchPage(listOf("$q-$p"), p, 1) }
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            advanceUntilIdle()
            assertEquals("alien", (search.state.value as QuerySearchState.Failed).query)
            assertTrue((search.state.value as QuerySearchState.Failed).cause is IOException)
            fail = false
            search.retry()
            advanceUntilIdle()
            assertEquals(2, calls.size)
            assertEquals(QuerySearchState.Results("alien", listOf("alien-1"), hasMore = false), search.state.value)
        }

    @Test
    fun `further pages append in order until the results run out`() =
        runTest {
            val search = newSearch(scope())
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            advanceUntilIdle()
            search.loadMore()
            advanceUntilIdle()
            search.loadMore()
            advanceUntilIdle()
            search.loadMore()
            advanceUntilIdle()
            assertEquals(QuerySearchState.Results("alien", listOf("alien-1", "alien-2", "alien-3"), hasMore = false), search.state.value)
            assertEquals(listOf("alien" to 1, "alien" to 2, "alien" to 3), calls)
        }

    @Test
    fun `an empty first page is read past while more remain`() =
        runTest {
            val search =
                newSearch(scope(), fetch = { q, p -> SearchPage(if (p == 1) emptyList() else listOf("$q-$p"), p, 2) })
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            advanceUntilIdle()
            assertEquals(QuerySearchState.Results("alien", listOf("alien-2"), hasMore = false), search.state.value)
        }

    @Test
    fun `a page of repeats is read past and repeats are dropped`() =
        runTest {
            val pages = mapOf(1 to listOf("a", "b"), 2 to listOf("b", "a"), 3 to listOf("c"))
            val search =
                newSearch(scope(), fetch = { _, p -> SearchPage(pages.getValue(p), p, 3) }, keyOf = { it })
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            advanceUntilIdle()
            search.loadMore()
            advanceUntilIdle()
            assertEquals(QuerySearchState.Results("alien", listOf("a", "b", "c"), hasMore = false), search.state.value)
        }

    @Test
    fun `a page that fails keeps the rows and can be asked for again`() =
        runTest {
            var fail = true
            val search =
                newSearch(scope()) { q, p -> if (p == 2 && fail) throw IOException("down") else SearchPage(listOf("$q-$p"), p, 2) }
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            advanceUntilIdle()
            search.loadMore()
            advanceUntilIdle()
            assertEquals(QuerySearchState.Results("alien", listOf("alien-1"), hasMore = true, appendFailed = true), search.state.value)
            fail = false
            search.loadMore()
            advanceUntilIdle()
            assertEquals(QuerySearchState.Results("alien", listOf("alien-1", "alien-2"), hasMore = false), search.state.value)
        }

    @Test
    fun `a page in flight is dropped when the query changes`() =
        runTest {
            val slow = CompletableDeferred<SearchPage<String>>()
            val search =
                newSearch(scope()) { q, p -> if (q == "alien" && p == 2) slow.await() else SearchPage(listOf("$q-$p"), p, 2) }
            scope().launch { search.state.collect {} }
            search.setQuery("alien")
            advanceUntilIdle()
            search.loadMore()
            advanceUntilIdle()
            search.setQuery("predator")
            advanceUntilIdle()
            slow.complete(SearchPage(listOf("late"), 2, 2))
            advanceUntilIdle()
            assertEquals(QuerySearchState.Results("predator", listOf("predator-1"), hasMore = true), search.state.value)
        }

    @Test
    fun `clearing the query returns to the recents and forgetting empties them`() =
        runTest {
            val history = FakeHistory(listOf("alien"))
            val search = newSearch(scope(), history)
            scope().launch { search.state.collect {} }
            search.setQuery("predator")
            advanceUntilIdle()
            search.clear()
            advanceUntilIdle()
            assertEquals(QuerySearchState.Idle(listOf("predator", "alien")), search.state.value)
            search.forgetRecents()
            advanceUntilIdle()
            assertEquals(QuerySearchState.Idle(emptyList()), search.state.value)
        }
}
