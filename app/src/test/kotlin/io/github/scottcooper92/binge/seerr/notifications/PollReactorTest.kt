package io.github.scottcooper92.binge.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PollReactorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()

    @Test
    fun `a clean run succeeds and a transient failure retries, neither touching the schedule`() =
        runTest {
            val prefs = NotificationPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") })
            val reactor = PollReactor(notifier, scheduler, prefs)
            assertEquals(PollOutcome.Succeed, reactor.react(CheckResult.Ok))
            assertEquals(PollOutcome.Retry, reactor.react(CheckResult.TransientFailure))
            assertEquals(emptyList<String>(), scheduler.calls)
            assertEquals(0, notifier.connectionProblems)
            assertFalse(prefs.pausedForAuthFailure.first())
        }

    @Test
    fun `a rejected credential asks the user to sign in again, pauses the poll, and persists the pause`() =
        runTest {
            val prefs = NotificationPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") })
            val reactor = PollReactor(notifier, scheduler, prefs)
            assertEquals(PollOutcome.Succeed, reactor.react(CheckResult.AuthFailure))
            assertEquals(listOf("cancel"), scheduler.calls)
            assertEquals(1, notifier.connectionProblems)
            assertTrue(prefs.pausedForAuthFailure.first())
        }
}
