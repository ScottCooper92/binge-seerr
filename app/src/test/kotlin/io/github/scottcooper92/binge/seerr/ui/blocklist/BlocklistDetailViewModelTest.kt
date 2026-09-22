package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val TMDB_ID = 100
private const val ADDED_AT_MILLIS = 1_749_000_000_000L

/** The blocked title's own page: known synchronously off the route, the backdrop/overview/web url fetched after. */
class BlocklistDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    private val item =
        BlocklistItem(
            id = 11,
            tmdbId = TMDB_ID,
            mediaType = RequestMediaType.Movie,
            title = "Heat",
            posterUrl = null,
            year = "1995",
            addedBy = "Ada",
            addedAtMillis = ADDED_AT_MILLIS,
            tags = listOf("4K"),
        )

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve(
            "GET /api/v1/movie/100",
            """{"title":"Heat","releaseDate":"1995-12-15","backdropPath":"/heat.jpg","overview":"A crew and a cop."}""",
        )
        seerr.serve("DELETE /api/v1/blocklist/100", "", code = 204)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(canManage: Boolean = true): BlocklistDetailViewModel {
        val vm = BlocklistDetailViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, item, canManage)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `the item's own fields render immediately, the lookup fills in the backdrop, overview and web url after`() =
        runTest {
            val vm = viewModel()
            assertEquals("Heat", vm.uiState.value.item.title)
            assertTrue(vm.uiState.value.canManage)
            assertEquals("", vm.uiState.value.webUrl)

            val ready = vm.uiState.first { it.webUrl.isNotEmpty() }
            assertEquals(seerr.server.url("/movie/100").toString(), ready.webUrl)
            assertEquals("https://image.tmdb.org/t/p/w1280/heat.jpg", ready.backdropUrl)
            assertEquals("A crew and a cop.", ready.overview)
        }

    @Test
    fun `a viewer without MANAGE_BLOCKLIST carries no manage flag through from the route`() =
        runTest {
            val vm = viewModel(canManage = false)
            assertFalse(vm.uiState.value.canManage)
        }

    @Test
    fun `unblocking deletes by TMDB id at the server's path, then reports removed`() =
        runTest {
            val vm = viewModel()
            val removed = awaitEvent(vm.events)
            vm.unblock()
            assertEquals(BlocklistDetailEvent.Removed, removed.await())
            assertEquals(1, seerr.count("DELETE", "/api/v1/blocklist/100"))
            // Seerr 3.2 made this required and answers 400 without it.
            val delete = seerr.received.last { it.method == "DELETE" }
            assertEquals("movie", delete.url.queryParameter("mediaType"))
        }

    @Test
    fun `a failed unblock reports failed and leaves the action ready to retry`() =
        runTest {
            seerr.remove("DELETE /api/v1/blocklist/100")
            val vm = viewModel()
            val failed = awaitEvent(vm.events)
            vm.unblock()
            assertTrue(failed.await() is BlocklistDetailEvent.Failed)
            assertFalse(vm.uiState.value.unblocking)
        }
}
