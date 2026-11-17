package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.ServerIdentity
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.telemetry.DiscardLogger
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.FakeTitleDao
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.notifications.PollScheduler
import com.cooper.homelab.core.telemetry.NoOpCrashBreadcrumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections
import com.cooper.homelab.core.notifications.PollReactor as SharedPollReactor

private const val WAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 10L
private val SERVER = ServerId("server-1")

private class FakePollScheduler : PollScheduler {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val nextRun = MutableStateFlow<Long?>(null)

    override fun scheduleNoSoonerThan(minutes: Int) {
        calls += "schedule:$minutes"
    }

    override fun cancel() {
        calls += "cancel"
    }

    override fun nextRunMillis(): Flow<Long?> = nextRun
}

/** The shared reactor and planner over a real connection and DataStore: the same outcomes the app's own reactor and planner give. */
class SharedPollingReactionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val notifier = FakeNotifier()
    private val scheduler = FakePollScheduler()
    private var stores = 0

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 7, permissions = ADMIN)
        seerr.serve("GET /api/v1/request", """{"pageInfo":{"pages":1,"results":0},"results":[]}""")
    }

    @After
    fun tearDown() = seerr.close()

    private fun TestScope.dataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p${stores++}.preferences_pb") }

    private suspend fun awaitCalls(expected: List<String>) =
        withContext(Dispatchers.Default) { withTimeout(WAIT_MILLIS) { while (scheduler.calls.toList() != expected) delay(POLL_MILLIS) } }

    private suspend fun TestScope.poll(prefs: NotificationPrefs): SharedNotificationPoll {
        prefs.setEnabled(NotificationSignal.PendingRequests, true)
        val connection = seerr.connection(this)
        val checker =
            NotificationsChecker(
                prefs,
                connection,
                NotificationFeeds(connection, TitleCache(FakeTitleDao()), DiscardLogger),
                notifier,
                PrefsCursorFeeds(prefs),
            )
        val reactor =
            SharedPollReactor(
                notifier = SeerrPollNotifier(notifier),
                scheduler = scheduler,
                pause = PrefsPollPauseStore(prefs),
                onStopPolling = {},
                anyPollable = { anyPollable(connection, prefs) },
            )
        assertTrue(anyPollable(connection, prefs))
        return SharedNotificationPoll(checker, reactor, { SERVER }, prefs)
    }

    @Test
    fun `a clean run succeeds, records when it ran and leaves the schedule alone`() =
        runTest {
            val prefs = NotificationPrefs(dataStore())
            val poll = poll(prefs)

            assertEquals(PollOutcome.Succeed, poll.run())

            assertEquals(emptyList<String>(), scheduler.calls.toList())
            assertEquals(0, notifier.connectionProblems)
            assertTrue(prefs.lastRunMillis.first() != null)
        }

    @Test
    fun `a failing server retries and touches nothing`() =
        runTest {
            val prefs = NotificationPrefs(dataStore())
            val poll = poll(prefs)
            seerr.serve("GET /api/v1/request", "", code = 500)

            assertEquals(PollOutcome.Retry, poll.run())

            assertEquals(emptyList<String>(), scheduler.calls.toList())
            assertFalse(prefs.pausedForAuthFailure.first())
        }

    @Test
    fun `a rejected credential posts the notice once, pauses the poll and cancels the schedule`() =
        runTest {
            val prefs = NotificationPrefs(dataStore())
            val poll = poll(prefs)
            seerr.serve("GET /api/v1/request", "", code = 401)
            seerr.serve("GET /api/v1/auth/me", "", code = 401)

            assertEquals(PollOutcome.Succeed, poll.run())

            assertEquals(1, notifier.connectionProblems)
            assertTrue(prefs.pausedForAuthFailure.first())
            assertEquals(listOf("cancel"), scheduler.calls.toList())
        }

    @Test
    fun `the planner schedules a connected enabled server, cancels a paused one, and clears the notice when it is not paused`() =
        runTest {
            val prefs = NotificationPrefs(dataStore())
            val connection = seerr.connection(this)
            val identity = ServerIdentity(dataStore())
            SharedPlanning(
                connection,
                prefs,
                identity,
                scheduler,
                SeerrPollNotifier(notifier),
                backgroundScope,
                NoOpCrashBreadcrumbs,
            ).start()
            awaitCalls(listOf("cancel"))

            prefs.setEnabled(NotificationSignal.PendingRequests, true)
            awaitCalls(listOf("cancel", "schedule:$DEFAULT_POLL_INTERVAL_MINUTES"))

            prefs.setPausedForAuthFailure(true)
            awaitCalls(listOf("cancel", "schedule:$DEFAULT_POLL_INTERVAL_MINUTES", "cancel"))

            prefs.setPausedForAuthFailure(false)
            awaitCalls(listOf("cancel", "schedule:$DEFAULT_POLL_INTERVAL_MINUTES", "cancel", "schedule:$DEFAULT_POLL_INTERVAL_MINUTES"))
            assertTrue(notifier.connectionProblemCancels >= 1)
        }

    @Test
    fun `the scheduler adapter passes the interval, the cancel and the next run through`() =
        runTest {
            val adapter = SharedNotificationScheduler(scheduler)

            adapter.schedule(30)
            adapter.cancel()

            assertEquals(listOf("schedule:30", "cancel"), scheduler.calls.toList())
            assertEquals(null, adapter.nextRunMillis().first())
        }
}
