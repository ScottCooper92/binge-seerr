package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
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
        val vm = JobsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
        vm.runningRefreshMillis = 10
        vm.outcomeMillis = 10
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
    fun `run with a notice reports success on the same endpoint the plain run uses`() =
        runTest {
            // running = false: this test is about the notice, not the running-poll loop — a true
            // here would launch followRunning() and leave it unawaited past this test's own scope,
            // the same dangling-coroutine trap #177 documents for a delay-loop under a virtual clock.
            seerr.serve("POST /api/v1/settings/jobs/plex-full-scan/run", job("plex-full-scan", running = false))
            val vm = viewModel()
            vm.awaitReady()
            val notice = awaitEvent(vm.events)
            vm.run(MEDIA_SERVER_SCAN_JOB_ID, R.string.tv_settings_scan_started)
            assertEquals(EditorEvent.Notice(R.string.tv_settings_scan_started), notice.await())
            assertEquals(1, seerr.count("POST", "/api/v1/settings/jobs/plex-full-scan/run"))
        }

    @Test
    fun `run with a notice still reports a failure, not the notice`() =
        runTest {
            seerr.serve("POST /api/v1/settings/jobs/plex-full-scan/run", """{"message":"boom"}""", code = 500)
            val vm = viewModel()
            vm.awaitReady()
            val failed = awaitEvent(vm.events)
            vm.run(MEDIA_SERVER_SCAN_JOB_ID, R.string.tv_settings_scan_started)
            assertTrue(failed.await() is EditorEvent.Failed)
        }

    @Test
    fun `a tap before the load resolves waits for it rather than being dropped`() =
        runTest {
            seerr.serveFrom("GET /api/v1/settings/jobs", delayMillis = 50) { JOBS }
            seerr.serve("POST /api/v1/settings/jobs/plex-full-scan/run", job("plex-full-scan", running = false))
            val vm = viewModel()
            val notice = awaitEvent(vm.events)
            vm.runWhenReady(MEDIA_SERVER_SCAN_JOB_ID, R.string.tv_settings_scan_started)
            assertEquals(EditorEvent.Notice(R.string.tv_settings_scan_started), notice.await())
        }

    @Test
    fun `a tap on a failed load retries it once, then runs once that retry lands`() =
        runTest {
            seerr.serve("GET /api/v1/settings/jobs", """{"message":"boom"}""", code = 500)
            seerr.serve("POST /api/v1/settings/jobs/plex-full-scan/run", job("plex-full-scan", running = false))
            val vm = viewModel()
            vm.uiState.first { it is JobsUiState.Error }
            seerr.serve("GET /api/v1/settings/jobs", JOBS)
            val notice = awaitEvent(vm.events)
            vm.runWhenReady(MEDIA_SERVER_SCAN_JOB_ID, R.string.tv_settings_scan_started)
            assertEquals(EditorEvent.Notice(R.string.tv_settings_scan_started), notice.await())
        }

    @Test
    fun `a tap on a load still failing after the retry reports the failure rather than doing nothing`() =
        runTest {
            seerr.serve("GET /api/v1/settings/jobs", """{"message":"boom"}""", code = 500)
            val vm = viewModel()
            vm.uiState.first { it is JobsUiState.Error }
            val failed = awaitEvent(vm.events)
            vm.runWhenReady(MEDIA_SERVER_SCAN_JOB_ID, R.string.tv_settings_scan_started)
            assertTrue(failed.await() is EditorEvent.Failed)
        }

    @Test
    fun `running a job that finishes immediately shows a success outcome, then clears it`() =
        runTest {
            seerr.serve("POST /api/v1/settings/jobs/download-sync/run", job("download-sync", running = false))
            val vm = viewModel()
            vm.awaitReady()
            vm.run("download-sync")
            val succeeded = vm.uiState.first { it is JobsUiState.Ready && it.outcomes["download-sync"] != null } as JobsUiState.Ready
            assertEquals(JobOutcome.Succeeded, succeeded.outcomes["download-sync"])
            val cleared = vm.uiState.first { it is JobsUiState.Ready && it.outcomes.isEmpty() } as JobsUiState.Ready
            assertTrue(cleared.outcomes.isEmpty())
        }

    @Test
    fun `running a job that the server refuses shows a failure outcome, then clears it`() =
        runTest {
            seerr.serve("POST /api/v1/settings/jobs/download-sync/run", """{"message":"boom"}""", code = 500)
            val vm = viewModel()
            vm.awaitReady()
            vm.run("download-sync")
            val failed = vm.uiState.first { it is JobsUiState.Ready && it.outcomes["download-sync"] != null } as JobsUiState.Ready
            assertEquals(JobOutcome.Failed, failed.outcomes["download-sync"])
            val cleared = vm.uiState.first { it is JobsUiState.Ready && it.outcomes.isEmpty() } as JobsUiState.Ready
            assertTrue(cleared.outcomes.isEmpty())
        }

    @Test
    fun `a job that was still running shows its outcome once the poll sees it stop, not before`() =
        runTest {
            seerr.serve("POST /api/v1/settings/jobs/plex-full-scan/run", job("plex-full-scan", running = true))
            val vm = viewModel()
            vm.awaitReady()
            vm.run("plex-full-scan")
            val running = vm.uiState.first { it is JobsUiState.Ready && it.jobs[0].running } as JobsUiState.Ready
            assertTrue(running.outcomes.isEmpty())

            seerr.serve("GET /api/v1/settings/jobs", JOBS)
            val succeeded = vm.uiState.first { it is JobsUiState.Ready && it.outcomes["plex-full-scan"] != null } as JobsUiState.Ready
            assertEquals(JobOutcome.Succeeded, succeeded.outcomes["plex-full-scan"])
        }

    /** A job the schedule starts while the page is open shows as running, and its next run moves on once it stops. */
    @Test
    fun `the list is re-read when the earliest next run comes due, and follows the job it started`() =
        runTest {
            val vm = viewModel()
            // An hour before the download sync's next run (05:01 UTC).
            vm.clock = { 1_789_275_660_000L - 60 * 60_000L }
            vm.reload()
            vm.awaitReady()
            val before = seerr.count("GET", "/api/v1/settings/jobs")

            seerr.serve(
                "GET /api/v1/settings/jobs",
                JOBS.replace(
                    """"download-sync","name":"Download Sync","type":"command","interval":"short","nextExecutionTime":"2026-09-13T05:01:00.000Z","running":false""",
                    """"download-sync","name":"Download Sync","type":"command","interval":"short","nextExecutionTime":"2026-09-13T05:02:00.000Z","running":true""",
                ),
            )
            val running = vm.uiState.first { it is JobsUiState.Ready && it.jobs[1].running } as JobsUiState.Ready
            assertTrue(running.jobs[1].running)
            assertTrue(seerr.count("GET", "/api/v1/settings/jobs") > before)

            seerr.serve("GET /api/v1/settings/jobs", JOBS)
            vm.uiState.first { it is JobsUiState.Ready && !it.jobs[1].running }
        }

    /** #936: the TV settings board holds this view model only to run a scan, and never shows the list. */
    @Test
    fun `a holder that never shows the list does not re-read it when a run comes due`() =
        runTest {
            val vm = JobsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            viewModels.put(vm.hashCode().toString(), vm)
            vm.clock = { 1_789_275_660_000L - 60 * 60_000L }
            vm.reload()
            vm.uiState.first { it is JobsUiState.Ready }
            val reads = seerr.count("GET", "/api/v1/settings/jobs")

            testScheduler.advanceTimeBy(2 * 60 * 60_000L)
            testScheduler.runCurrent()
            assertEquals(reads, seerr.count("GET", "/api/v1/settings/jobs"))
        }

    @Test
    fun `a page back on screen re-reads the list when a run came due while it was away`() =
        runTest {
            // Not viewModel(): its background collector would keep the page on screen the whole time.
            val vm = JobsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            viewModels.put(vm.hashCode().toString(), vm)
            var now = 1_789_275_660_000L - 60 * 60_000L
            vm.clock = { now }
            vm.reload()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
            vm.awaitReady()
            testScheduler.runCurrent()
            // The view model's own first read and the reload above: wait for both to land before counting.
            seerr.awaitCount("GET", "/api/v1/settings/jobs", moreThan = 1)
            testScheduler.runCurrent()
            collector.cancel()
            testScheduler.runCurrent()
            val reads = seerr.count("GET", "/api/v1/settings/jobs")

            now += 2 * 60 * 60_000L
            val returned = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
            testScheduler.runCurrent()
            seerr.awaitCount("GET", "/api/v1/settings/jobs", moreThan = reads)
            // runTest moves virtual time on while awaitCount waits in real time, so a later run coming due can add a read.
            assertTrue(seerr.count("GET", "/api/v1/settings/jobs") > reads)
            returned.cancel()
        }

    @Test
    fun `a list read while the page is away still lets it catch a run that came due`() =
        runTest {
            val vm = JobsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            viewModels.put(vm.hashCode().toString(), vm)
            var now = 1_789_275_660_000L - 60 * 60_000L
            vm.clock = { now }
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
            vm.awaitReady()
            collector.cancel()
            testScheduler.runCurrent()

            // A read that lands with nothing collecting must keep the run it found, not drop it.
            vm.reload()
            seerr.awaitCount("GET", "/api/v1/settings/jobs", moreThan = 1)
            // As below: the read's schedule is worked out on OkHttp's thread, so let it land before the clock moves (#1226).
            seerr.awaitCallbacks()
            testScheduler.runCurrent()
            val reads = seerr.count("GET", "/api/v1/settings/jobs")

            now += 2 * 60 * 60_000L
            val returned = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
            testScheduler.runCurrent()
            seerr.awaitCount("GET", "/api/v1/settings/jobs", moreThan = reads)
            assertTrue(seerr.count("GET", "/api/v1/settings/jobs") > reads)
            returned.cancel()
        }

    /** #946: a due read cut off by the page leaving must leave the missed run for the page's return to catch. */
    @Test
    fun `a due read cancelled in flight by the page leaving is read again on its return`() =
        runTest {
            val vm = JobsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            viewModels.put(vm.hashCode().toString(), vm)
            // An hour before the download sync's next run (05:01 UTC).
            var now = 1_789_275_660_000L - 60 * 60_000L
            vm.clock = { now }
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
            vm.awaitReady()
            seerr.awaitCount("GET", "/api/v1/settings/jobs", moreThan = 0)
            // The load's schedule is worked out on OkHttp's thread after Ready lands: wait for it before moving the clock (#1226).
            seerr.awaitCallbacks()
            testScheduler.runCurrent()
            val beforeDue = seerr.count("GET", "/api/v1/settings/jobs")

            val held = seerr.serveHeld("GET /api/v1/settings/jobs")
            now += 2 * 60 * 60_000L
            testScheduler.advanceTimeBy(2 * 60 * 60_000L)
            testScheduler.runCurrent()
            seerr.awaitCount("GET", "/api/v1/settings/jobs", moreThan = beforeDue)

            collector.cancel()
            testScheduler.runCurrent()
            held.release(code = 503)
            testScheduler.runCurrent()
            val reads = seerr.count("GET", "/api/v1/settings/jobs")

            val returned = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
            testScheduler.runCurrent()
            // Holding time: otherwise the plex scan's run, a day ahead, comes due during the wait and reads the list too.
            assertTrue(seerr.awaitCountHoldingTime("GET", "/api/v1/settings/jobs", moreThan = reads))
            returned.cancel()
        }

    /** #935: "in 20 minutes" counts down between reads while the list shows, and nothing ticks while it is away. */
    @Test
    fun `the rows' clock moves on each minute while the list is showing, and not while it is away`() =
        runTest {
            val vm = JobsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            viewModels.put(vm.hashCode().toString(), vm)
            val start = 1_789_275_600_000L + 30_000L
            vm.clock = { start + testScheduler.currentTime }
            // A ticker left running would keep the scheduler busy and hang the test rather than fail it.
            try {
                val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
                val first = vm.awaitReady().now

                testScheduler.advanceTimeBy(60_000L)
                testScheduler.runCurrent()
                val ticked = (vm.uiState.value as JobsUiState.Ready).now
                assertTrue(ticked > first)

                collector.cancel()
                testScheduler.runCurrent()
                testScheduler.advanceTimeBy(5 * 60_000L)
                testScheduler.runCurrent()
                assertEquals(ticked, (vm.uiState.value as JobsUiState.Ready).now)
            } finally {
                viewModels.clear()
            }
        }

    /** #935: the running poll serves a page showing the list; a page back on screen may have missed the job stopping. */
    @Test
    fun `a running job's poll stops while the page is away and reads at once on its return`() =
        runTest {
            seerr.serve("GET /api/v1/settings/jobs", "[${job("plex-full-scan", running = true)}]")
            val vm = JobsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            viewModels.put(vm.hashCode().toString(), vm)
            vm.runningRefreshMillis = 10
            // The job never stops, so a poll left running would keep the scheduler busy and hang the test, not fail it.
            try {
                val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
                vm.awaitReady()
                collector.cancel()
                testScheduler.runCurrent()
                // Let a read the poll had in flight as the page left land before counting.
                seerr.awaitCountHoldingTime("GET", "/api/v1/settings/jobs", moreThan = Int.MAX_VALUE)
                val reads = seerr.count("GET", "/api/v1/settings/jobs")

                testScheduler.advanceTimeBy(100 * vm.runningRefreshMillis)
                testScheduler.runCurrent()
                assertFalse(seerr.awaitCountHoldingTime("GET", "/api/v1/settings/jobs", moreThan = reads))

                val returned = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect {} }
                testScheduler.runCurrent()
                assertTrue(seerr.awaitCountHoldingTime("GET", "/api/v1/settings/jobs", moreThan = reads))
                returned.cancel()
            } finally {
                viewModels.clear()
            }
        }

    @Test
    fun `a next run already past schedules no re-read`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            val reads = seerr.count("GET", "/api/v1/settings/jobs")
            testScheduler.advanceTimeBy(24 * 60 * 60_000L)
            testScheduler.runCurrent()
            assertEquals(reads, seerr.count("GET", "/api/v1/settings/jobs"))
        }

    @Test
    fun `a preset encodes as the six-field cron the server takes, and a failure is reported`() =
        runTest {
            seerr.serve("POST /api/v1/settings/jobs/download-sync/schedule", job("download-sync", running = false))
            val vm = viewModel()
            vm.awaitReady()
            val scheduled = awaitEvent(vm.events)
            vm.schedule("download-sync", MINUTE_PRESETS.first { it.every == 15 }.cron)
            assertEquals(EditorEvent.Notice(R.string.server_settings_job_scheduled), scheduled.await())
            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/jobs/download-sync/schedule")).jsonObject
            assertEquals("0 */15 * * * *", sent.getValue("schedule").jsonPrimitive.content)
            assertEquals("0 0 0 */7 * *", HOUR_PRESETS.last().cron)

            seerr.serve("POST /api/v1/settings/jobs/download-sync/cancel", """{"message":"not running"}""", code = 400)
            val failed = awaitEvent(vm.events)
            vm.cancel("download-sync")
            assertTrue(failed.await() is EditorEvent.Failed)
            // Awaited, not sampled: reading `uiState.value` here asserts on whatever the state
            // happens to be at this line, which passes or fails on scheduling rather than on the
            // behaviour. The ViewModel releases the job before emitting, so this settles.
            vm.uiState.first { it is JobsUiState.Ready && it.busyIds.isEmpty() }
        }
}
