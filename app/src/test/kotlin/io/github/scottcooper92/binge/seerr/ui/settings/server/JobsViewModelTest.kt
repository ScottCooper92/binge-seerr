package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val JOBS =
    """[{"id":"plex-full-scan","name":"Plex Full Library Scan","type":"process","interval":"long","nextExecutionTime":"2026-09-14T03:00:00.000Z","running":false},
        {"id":"download-sync","name":"Download Sync","type":"command","interval":"short","nextExecutionTime":"2026-09-13T05:01:00.000Z","running":false},
        {"id":"image-cache-cleanup","name":"Image Cache Cleanup","type":"process","interval":"fixed","running":false}]"""

private fun job(
    id: String,
    running: Boolean,
) = """{"id":"$id","name":"$id","type":"process","interval":"long","nextExecutionTime":"2026-09-14T03:00:00.000Z","running":$running}"""

class JobsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/jobs", JOBS)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): JobsViewModel {
        val vm = JobsViewModel(seerr.connection(this))
        vm.runningRefreshMillis = 10
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun JobsViewModel.awaitReady(): JobsUiState.Ready = uiState.first { it is JobsUiState.Ready } as JobsUiState.Ready

    @Test
    fun `the jobs are read with their intervals, and only the short and long ones take a schedule`() =
        runTest {
            val jobs = viewModel().awaitReady().jobs
            assertEquals(listOf(JobInterval.Long, JobInterval.Short, JobInterval.Fixed), jobs.map { it.interval })
            assertEquals(listOf(true, true, false), jobs.map { it.schedulable })
            assertEquals(HOUR_PRESETS, jobs[0].presets())
            assertEquals(MINUTE_PRESETS, jobs[1].presets())
            assertEquals(1_789_354_800_000L, jobs[0].nextRunMillis)
            assertEquals(null, jobs[2].nextRunMillis)
        }

    @Test
    fun `running a job adopts the answer and re-reads the list until it stops`() =
        runTest {
            seerr.serve("POST /api/v1/settings/jobs/plex-full-scan/run", job("plex-full-scan", running = true))
            val vm = viewModel()
            vm.awaitReady()
            vm.run("plex-full-scan")
            val running = vm.uiState.first { it is JobsUiState.Ready && it.jobs[0].running } as JobsUiState.Ready
            assertTrue(running.jobs[0].running)

            seerr.serve("GET /api/v1/settings/jobs", JOBS)
            val settled = vm.uiState.first { it is JobsUiState.Ready && !it.jobs[0].running } as JobsUiState.Ready
            assertFalse(settled.jobs[0].running)
            assertTrue(seerr.count("GET", "/api/v1/settings/jobs") >= 2)
        }

    @Test
    fun `a preset encodes as the six-field cron the server takes, and a failure is reported`() =
        runTest {
            seerr.serve("POST /api/v1/settings/jobs/download-sync/schedule", job("download-sync", running = false))
            val vm = viewModel()
            vm.awaitReady()
            vm.schedule("download-sync", MINUTE_PRESETS.first { it.every == 15 }.cron)
            assertEquals(EditorEvent.Notice(R.string.server_settings_job_scheduled), vm.events.first())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/jobs/download-sync/schedule")).jsonObject
            assertEquals("0 */15 * * * *", sent.getValue("schedule").jsonPrimitive.content)
            assertEquals("0 0 0 */7 * *", HOUR_PRESETS.last().cron)

            seerr.serve("POST /api/v1/settings/jobs/download-sync/cancel", """{"message":"not running"}""", code = 400)
            vm.cancel("download-sync")
            assertTrue(vm.events.first() is EditorEvent.Failed)
            assertTrue(vm.awaitReady().busyIds.isEmpty())
        }
}
