package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingData
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val PAGE =
    """{"pageInfo":{"pages":1,"results":1},
        "results":[{"level":"info","message":"Server ready on port 5055","timestamp":"2026-09-13T05:00:00.000Z"}]}"""

class LogsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/logs", PAGE)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): LogsViewModel {
        val vm = LogsViewModel(seerr.connection(this))
        viewModels.put(vm.hashCode().toString(), vm)
        return vm
    }

    /**
     * One emission of [LogsViewModel.entries] is one query reaching the `Pager`. Collected rather
     * than snapshotted: a snapshot presents a generation, and what is under test is how many
     * generations there are.
     */
    private fun TestScope.generations(vm: LogsViewModel): List<PagingData<LogEntry>> =
        mutableListOf<PagingData<LogEntry>>().also { seen -> backgroundScope.launch { vm.entries.collect { seen += it } } }

    @Test
    fun `typing a search reaches the pager once, after the debounce rather than per keystroke`() =
        runTest {
            val vm = viewModel()
            val seen = generations(vm)
            runCurrent()
            // The blank query is not held back, so the first page is already on its way.
            assertEquals(1, seen.size)

            vm.setSearch("p")
            vm.setSearch("po")
            vm.setSearch("port")
            advanceTimeBy(SEARCH_DEBOUNCE_MS - 1)
            assertEquals(1, seen.size)

            advanceTimeBy(2)
            assertEquals(2, seen.size)
        }

    @Test
    fun `clearing the search is not held back, and re-picking the level the page is on re-queries nothing`() =
        runTest {
            val vm = viewModel()
            val seen = generations(vm)
            runCurrent()

            vm.setSearch("port")
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            assertEquals(2, seen.size)

            vm.setSearch("")
            runCurrent()
            assertEquals(3, seen.size)

            vm.setLevel(vm.uiState.value.level)
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            assertEquals(3, seen.size)
        }

    @Test
    fun `a refresh lands on the interval while following, and stops once it does not`() =
        runTest {
            val vm = viewModel()
            vm.refreshMillis = 1_000
            val events = mutableListOf<LogsEvent>()
            backgroundScope.launch { vm.events.collect { events += it } }

            vm.setFollowing(true)
            advanceTimeBy(2_500)
            assertEquals(listOf(LogsEvent.Refresh, LogsEvent.Refresh), events)

            vm.setFollowing(false)
            advanceTimeBy(10_000)
            assertEquals(2, events.size)
        }
}
