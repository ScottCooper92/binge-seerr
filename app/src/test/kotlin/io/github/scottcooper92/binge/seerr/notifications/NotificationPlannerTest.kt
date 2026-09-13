package io.github.scottcooper92.binge.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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
}
