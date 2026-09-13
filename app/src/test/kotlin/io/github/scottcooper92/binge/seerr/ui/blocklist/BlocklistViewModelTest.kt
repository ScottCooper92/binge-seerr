package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val VIEW_BLOCKLIST = 1 shl 30
private const val REQUEST_WAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 20L
private const val PAGE = """{"pageInfo":{"pages":1,"results":1},"results":[{"id":11,"tmdbId":100,"mediaType":"movie","title":"Heat"}]}"""

class BlocklistViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/blocklist", PAGE)
        seerr.serve("GET /api/v1/blacklist", PAGE)
        seerr.serve("GET /api/v1/movie/100", """{"title":"Heat","releaseDate":"1995-12-15"}""")
        seerr.serve("DELETE /api/v1/blocklist/100", "", code = 204)
        seerr.serve("POST /api/v1/blocklist/collection/5", "", code = 201)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): BlocklistViewModel {
        val vm = BlocklistViewModel(seerr.connection(this), TitleCache())
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun BlocklistViewModel.awaitReady(match: (BlocklistUiState.Ready) -> Boolean = { true }): BlocklistUiState.Ready =
        uiState.first { it is BlocklistUiState.Ready && match(it) } as BlocklistUiState.Ready

    @Test
    fun `a manager on Seerr 3 gets the source chips with their counts, and the rows titled`() =
        runTest {
            val vm = viewModel()
            vm.setScreenVisible(true)
            val ready = vm.awaitReady { it.counts?.all != null && it.canManage }
            assertTrue(ready.hasFilters)
            assertTrue(ready.canBlockCollections)
            assertEquals(BlocklistCounts(all = 1, manual = 1, tagged = 1), ready.counts)
            assertEquals(seerr.server.url("/").toString(), ready.webRoot)

            assertEquals(
                "Heat",
                vm.items
                    .asSnapshot()
                    .single()
                    .title,
            )
            assertTrue(received("GET", "/api/v1/blocklist").any { it.url.queryParameter("filter") == "manual" })
        }

    @Test
    fun `a viewer on Jellyseerr 2 reads one list at the old path, with nothing to manage`() =
        runTest {
            seerr.viewer(id = 2, permissions = VIEW_BLOCKLIST, version = "2.7.0")
            val vm = viewModel()
            vm.setScreenVisible(true)
            val ready = vm.awaitReady { !it.hasFilters }
            assertFalse(ready.canManage)
            assertFalse(ready.canBlockCollections)
            assertNull(ready.counts)
            assertEquals(
                "Heat",
                vm.items
                    .asSnapshot()
                    .single()
                    .title,
            )
            assertTrue(received("GET", "/api/v1/blacklist").isNotEmpty())
            assertTrue(received("GET", "/api/v1/blocklist").isEmpty())
        }

    @Test
    fun `removing deletes by TMDB id at the server's path, then refetches the counts`() =
        runTest {
            val vm = viewModel()
            vm.setScreenVisible(true)
            vm.awaitReady { it.counts != null }
            val item = vm.items.asSnapshot().single()
            val probes = received("GET", "/api/v1/blocklist").size

            vm.remove(item)
            assertEquals(BlocklistEvent.Removed, vm.events.first())
            assertEquals(1, received("DELETE", "/api/v1/blocklist/100").size)
            vm.awaitReady { it.actingTmdbIds.isEmpty() }
            awaitRequests("GET", "/api/v1/blocklist", moreThan = probes)
        }

    @Test
    fun `a collection is blocked only where the server can, and the call is skipped elsewhere`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady { it.canBlockCollections }
            vm.setCollectionBlocked(5, blocked = true)
            assertEquals(BlocklistEvent.CollectionChanged(blocked = true), vm.events.first())
            assertEquals(1, received("POST", "/api/v1/blocklist/collection/5").size)

            seerr.viewer(id = 1, permissions = ADMIN, version = "3.1.0")
            val older = viewModel()
            older.awaitReady { !it.canBlockCollections }
            older.setCollectionBlocked(5, blocked = true)
            older.awaitReady()
            assertEquals(1, received("POST", "/api/v1/blocklist/collection/5").size)
        }

    private fun received(
        method: String,
        path: String,
    ) = seerr.received.filter { it.method == method && it.url.encodedPath == path }

    /** The probes land on OkHttp's threads after the event; this waits for them in real time. */
    private suspend fun awaitRequests(
        method: String,
        path: String,
        moreThan: Int,
    ) = withContext(Dispatchers.Default) {
        withTimeout(REQUEST_WAIT_MILLIS) { while (received(method, path).size <= moreThan) delay(POLL_MILLIS) }
    }
}
