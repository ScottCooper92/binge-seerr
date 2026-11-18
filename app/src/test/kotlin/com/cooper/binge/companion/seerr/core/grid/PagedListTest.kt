package com.cooper.binge.companion.seerr.core.grid

import com.cooper.binge.companion.seerr.core.search.SearchPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PagedListTest {
    private val calls = mutableListOf<Pair<String, Int>>()
    private val jobs = mutableListOf<Job>()

    /** A scope on the test's scheduler that [advanceUntilIdle] counts, which `backgroundScope` is not; ended by [end]. */
    private fun TestScope.scope(): CoroutineScope = CoroutineScope(coroutineContext.minusKey(Job) + Job().also(jobs::add))

    @After
    fun end() = jobs.forEach(Job::cancel)

    private fun TestScope.listOver(
        query: MutableStateFlow<String>,
        fetch: suspend (String, Int) -> SearchPage<String> = { q, p -> SearchPage(listOf("$q-$p"), p, 3) },
    ): PagedList<String, String> {
        val list =
            PagedList(scope(), query, { q, p ->
                calls += q to p
                fetch(q, p)
            })
        scope().launch { list.state.collect {} }
        return list
    }

    @Test
    fun `the first page loads for the first query`() =
        runTest {
            val list = listOver(MutableStateFlow("a"))
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("a-1"), hasMore = true), list.state.value)
            assertEquals(listOf("a" to 1), calls)
        }

    @Test
    fun `an empty page with more behind it is read past`() =
        runTest {
            val list =
                listOver(MutableStateFlow("a")) { q, p ->
                    if (p == 2) SearchPage(emptyList(), p, 4) else SearchPage(listOf("$q-$p"), p, 4)
                }
            advanceUntilIdle()
            list.loadMore()
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("a-1", "a-3"), hasMore = true), list.state.value)
            assertEquals(listOf("a" to 1, "a" to 2, "a" to 3), calls)
        }

    @Test
    fun `an empty first page with more behind it is read past`() =
        runTest {
            val list =
                listOver(MutableStateFlow("a")) { q, p ->
                    if (p == 1) SearchPage(emptyList(), p, 2) else SearchPage(listOf("$q-$p"), p, 2)
                }
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("a-2"), hasMore = false), list.state.value)
        }

    @Test
    fun `a new query starts again from its first page`() =
        runTest {
            val query = MutableStateFlow("a")
            val list = listOver(query)
            advanceUntilIdle()
            list.loadMore()
            advanceUntilIdle()
            query.value = "b"
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("b-1"), hasMore = true), list.state.value)
            assertEquals(listOf("a" to 1, "a" to 2, "b" to 1), calls)
        }

    @Test
    fun `the same query again does not load again`() =
        runTest {
            val query = MutableStateFlow("a")
            listOver(query)
            advanceUntilIdle()
            query.value = "a"
            advanceUntilIdle()
            assertEquals(1, calls.size)
        }

    @Test
    fun `a query that changes mid-load drops the first result`() =
        runTest {
            val slow = CompletableDeferred<SearchPage<String>>()
            val query = MutableStateFlow("a")
            val list = listOver(query) { q, p -> if (q == "a") slow.await() else SearchPage(listOf("$q-$p"), p, 1) }
            advanceUntilIdle()
            assertEquals(PagedListState.Loading, list.state.value)
            query.value = "b"
            advanceUntilIdle()
            slow.complete(SearchPage(listOf("late"), 1, 1))
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("b-1"), hasMore = false), list.state.value)
        }

    @Test
    fun `no items is empty`() =
        runTest {
            val list = listOver(MutableStateFlow("a")) { _, p -> SearchPage(emptyList(), p, 1) }
            advanceUntilIdle()
            assertEquals(PagedListState.Empty, list.state.value)
        }

    @Test
    fun `a failure can be retried`() =
        runTest {
            var fail = true
            val list =
                listOver(MutableStateFlow("a")) { q, p -> if (fail) throw IOException("down") else SearchPage(listOf("$q-$p"), p, 1) }
            advanceUntilIdle()
            assertTrue((list.state.value as PagedListState.Failed).cause is IOException)
            fail = false
            list.retry()
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("a-1"), hasMore = false), list.state.value)
            assertEquals(2, calls.size)
        }

    @Test
    fun `further pages append in order until the results run out`() =
        runTest {
            val list = listOver(MutableStateFlow("a"))
            advanceUntilIdle()
            repeat(3) {
                list.loadMore()
                advanceUntilIdle()
            }
            assertEquals(PagedListState.Ready(listOf("a-1", "a-2", "a-3"), hasMore = false), list.state.value)
            assertEquals(listOf("a" to 1, "a" to 2, "a" to 3), calls)
        }

    @Test
    fun `a page that fails keeps the rows and can be asked for again`() =
        runTest {
            var fail = true
            val list =
                listOver(MutableStateFlow("a")) { q, p ->
                    if (p == 2 &&
                        fail
                    ) {
                        throw IOException("down")
                    } else {
                        SearchPage(listOf("$q-$p"), p, 2)
                    }
                }
            advanceUntilIdle()
            list.loadMore()
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("a-1"), hasMore = true, appendFailed = true), list.state.value)
            fail = false
            list.loadMore()
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("a-1", "a-2"), hasMore = false), list.state.value)
        }

    @Test
    fun `a page in flight is dropped when the query changes`() =
        runTest {
            val slow = CompletableDeferred<SearchPage<String>>()
            val query = MutableStateFlow("a")
            val list = listOver(query) { q, p -> if (q == "a" && p == 2) slow.await() else SearchPage(listOf("$q-$p"), p, 2) }
            advanceUntilIdle()
            list.loadMore()
            advanceUntilIdle()
            query.value = "b"
            advanceUntilIdle()
            slow.complete(SearchPage(listOf("late"), 2, 2))
            advanceUntilIdle()
            assertEquals(PagedListState.Ready(listOf("b-1"), hasMore = true), list.state.value)
        }

    @Test
    fun `sort and filters are held apart from what they mean and a change is a new query`() {
        val state = SortFilterState<Set<String>, String>(initialFilters = setOf("drama"), initialSort = "popular")
        assertEquals(SortFilterQuery(setOf("drama"), "popular"), state.query.value)
        state.setSort("newest")
        state.updateFilters { it + "comedy" }
        assertEquals(SortFilterQuery(setOf("drama", "comedy"), "newest"), state.query.value)
        state.setFilters(emptySet())
        assertEquals(SortFilterQuery(emptySet<String>(), "newest"), state.query.value)
    }
}
