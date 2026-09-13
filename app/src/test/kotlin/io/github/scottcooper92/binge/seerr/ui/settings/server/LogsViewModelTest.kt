package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
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

    @Test
    fun `the ticks land on the interval while following, and stop once it does not`() =
        runTest {
            val vm = viewModel()
            vm.refreshMillis = 1_000
            val ticks = mutableListOf<Unit>()
            backgroundScope.launch { vm.refreshTicks.collect { ticks += it } }

            vm.setFollowing(true)
            advanceTimeBy(2_500)
            assertEquals(2, ticks.size)

            vm.setFollowing(false)
            advanceTimeBy(10_000)
            assertEquals(2, ticks.size)
        }
}
