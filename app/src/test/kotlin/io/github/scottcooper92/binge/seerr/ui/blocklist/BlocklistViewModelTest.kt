package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.RecordingAnalytics
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
private const val PAGE = """{"pageInfo":{"pages":1,"results":1},"results":[{"id":11,"tmdbId":100,"mediaType":"movie","title":"Heat"}]}"""

class BlocklistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()
    private val analytics = RecordingAnalytics()

    @Before
    fun setUp() {
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
        val vm = BlocklistViewModel(seerr.connection(this), TitleCache(), mainDispatcherRule.dispatcher, analytics)
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

            assertEquals(
                "Heat",
                vm
                    .items(BlocklistFilter.All)
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
                vm
                    .items(BlocklistFilter.All)
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
            val item = vm.items(BlocklistFilter.All).asSnapshot().single()
            val probes = received("GET", "/api/v1/blocklist").size

            val removed = awaitEvent(vm.events)
            vm.remove(item)
            assertEquals(BlocklistEvent.Removed, removed.await())
            val deletes = received("DELETE", "/api/v1/blocklist/100")
            assertEquals(1, deletes.size)
            // Seerr 3.2 made this required and answers 400 without it.
            assertEquals("movie", deletes.single().url.queryParameter("mediaType"))
            vm.awaitReady { it.actingTmdbIds.isEmpty() }
            seerr.awaitCount("GET", "/api/v1/blocklist", moreThan = probes)
            assertEquals(listOf("blocklist_changed" to mapOf("action" to "removed")), analytics.events)
        }

    @Test
    fun `a collection is blocked only where the server can, and the call is skipped elsewhere`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady { it.canBlockCollections }
            val collectionChanged = awaitEvent(vm.events)
            vm.setCollectionBlocked(5, blocked = true)
            assertEquals(BlocklistEvent.CollectionChanged(blocked = true), collectionChanged.await())
            assertEquals(1, received("POST", "/api/v1/blocklist/collection/5").size)
            assertEquals(listOf("blocklist_changed" to mapOf("action" to "collection_blocked")), analytics.events)

            seerr.viewer(id = 1, permissions = ADMIN, version = "3.1.0")
            val older = viewModel()
            older.awaitReady { !it.canBlockCollections }
            older.setCollectionBlocked(5, blocked = true)
            older.awaitReady()
            assertEquals(1, received("POST", "/api/v1/blocklist/collection/5").size)
        }

    /**
     * The scope was resolved once per view model and never again, so a permission granted in the web
     * client stayed invisible for as long as the view model lived.
     */
    @Test
    fun `a permission granted on the server shows when the screen is next entered`() =
        runTest {
            seerr.viewer(id = 2, permissions = VIEW_BLOCKLIST)
            val vm = viewModel()
            vm.setScreenVisible(true)
            assertFalse(vm.awaitReady().canManage)

            seerr.viewer(id = 2, permissions = ADMIN)
            vm.setScreenVisible(true)

            assertTrue(vm.awaitReady { it.canManage }.canManage)
        }

    /** One read that failed on first entry used to fix `canManage` at false until the entry was recreated. */
    @Test
    fun `a first read that failed does not freeze the scope, the next entry recovers it`() =
        runTest {
            val connection = seerr.connection(this)
            // Drop the user connect() cached and have the next read fail, as a cold process on a bad network would.
            seerr.serve("GET /api/v1/auth/me", code = 500)
            runCatching { connection.refreshAuthenticatedUser() }
            val vm = BlocklistViewModel(connection, TitleCache(), mainDispatcherRule.dispatcher, analytics)
            viewModels.put(vm.hashCode().toString(), vm)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.setScreenVisible(true)
            assertFalse(vm.awaitReady().canManage)

            seerr.viewer(id = 1, permissions = ADMIN)
            vm.setScreenVisible(true)

            assertTrue(vm.awaitReady { it.canManage }.canManage)
        }

    private fun received(
        method: String,
        path: String,
    ) = seerr.received.filter { it.method == method && it.url.encodedPath == path }
}
