package io.github.scottcooper92.binge.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

private const val WAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 10L

/** The schedule follows the connection and the toggles, over a real connection and a real DataStore. */
class NotificationPlannerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 7, permissions = ADMIN)
    }

    @After
    fun tearDown() = seerr.close()

    private suspend fun awaitCalls(expected: List<String>) =
        withContext(Dispatchers.Default) { withTimeout(WAIT_MILLIS) { while (scheduler.calls != expected) delay(POLL_MILLIS) } }

    private suspend fun awaitLastCall(expected: String) =
        withContext(Dispatchers.Default) {
            withTimeout(WAIT_MILLIS) { while (scheduler.calls.lastOrNull() != expected) delay(POLL_MILLIS) }
        }

    private suspend fun awaitConnectionProblemCancels(expected: Int) =
        withContext(Dispatchers.Default) {
            withTimeout(WAIT_MILLIS) { while (notifier.connectionProblemCancels != expected) delay(POLL_MILLIS) }
        }

    @Test
    fun `scheduled while connected with a signal on, cancelled otherwise, and re-armed by new credentials`() =
        runTest {
            val prefs = NotificationPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") })
            val connection = seerr.connection(this)
            NotificationPlanner(connection, prefs, scheduler, notifier, backgroundScope).start()
            awaitCalls(listOf("cancel"))

            prefs.setEnabled(NotificationSignal.PendingRequests, true)
            awaitCalls(listOf("cancel", "schedule"))

            connection.connect(seerr.server.url("/").toString(), SeerrAuth.ApiKey("other")).getOrThrow()
            awaitCalls(listOf("cancel", "schedule", "schedule"))

            prefs.setEnabled(NotificationSignal.PendingRequests, false)
            awaitCalls(listOf("cancel", "schedule", "schedule", "cancel"))

            prefs.setEnabled(NotificationSignal.OpenIssues, true)
            awaitCalls(listOf("cancel", "schedule", "schedule", "cancel", "schedule"))
            connection.disconnect()
            awaitCalls(listOf("cancel", "schedule", "schedule", "cancel", "schedule", "cancel"))
            assertEquals(1, notifier.activityCancels)
        }

    @Test
    fun `a same-key reconnect after a rejected credential still re-arms the poll the reactor paused`() =
        runTest {
            val prefs = NotificationPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") })
            val connection = seerr.connection(this, onServerChanged = { prefs.forgetServer() })
            NotificationPlanner(connection, prefs, scheduler, notifier, backgroundScope).start()
            awaitLastCall("cancel")

            prefs.setEnabled(NotificationSignal.PendingRequests, true)
            awaitLastCall("schedule")

            PollReactor(notifier, scheduler, prefs).react(CheckResult.AuthFailure)
            awaitLastCall("cancel")
            assertTrue(prefs.pausedForAuthFailure.first())
            assertEquals(1, notifier.connectionProblems)
            // The reactor's own notice must survive the re-plan the pause itself triggers.
            val cancelsWhilePaused = notifier.connectionProblemCancels

            // The same key that was just rejected: a structural comparison of the credentials sees no change.
            connection.connect(seerr.server.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
            awaitLastCall("schedule")
            assertFalse(prefs.pausedForAuthFailure.first())
            assertEquals(cancelsWhilePaused, notifier.connectionProblemCancels - 1)
        }

    @Test
    fun `disconnecting while paused for an auth failure still cancels the connection-problem notice`() =
        runTest {
            val prefs = NotificationPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") })
            val connection = seerr.connection(this, onServerChanged = { prefs.forgetServer() })
            NotificationPlanner(connection, prefs, scheduler, notifier, backgroundScope).start()
            awaitLastCall("cancel")

            prefs.setEnabled(NotificationSignal.PendingRequests, true)
            awaitLastCall("schedule")

            PollReactor(notifier, scheduler, prefs).react(CheckResult.AuthFailure)
            awaitLastCall("cancel")
            assertEquals(1, notifier.connectionProblems)
            val cancelsWhilePaused = notifier.connectionProblemCancels

            // Disconnecting while still paused: `connected` and `paused` both flip, and the stuck
            // "sign in again" notice must be cancelled even though `!plan.connected` is also true.
            connection.disconnect()
            awaitConnectionProblemCancels(cancelsWhilePaused + 1)
            assertTrue(notifier.activityCancels >= 1)
        }
}
