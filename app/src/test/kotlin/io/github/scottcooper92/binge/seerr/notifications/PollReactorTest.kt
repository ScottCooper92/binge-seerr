package io.github.scottcooper92.binge.seerr.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class PollReactorTest {
    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()
    private val reactor = PollReactor(notifier, scheduler)

    @Test
    fun `a clean run succeeds and a transient failure retries, neither touching the schedule`() {
        assertEquals(PollOutcome.Succeed, reactor.react(CheckResult.Ok))
        assertEquals(PollOutcome.Retry, reactor.react(CheckResult.TransientFailure))
        assertEquals(emptyList<String>(), scheduler.calls)
        assertEquals(0, notifier.connectionProblems)
    }

    @Test
    fun `a rejected credential asks the user to sign in again and pauses the poll rather than retrying`() {
        assertEquals(PollOutcome.Succeed, reactor.react(CheckResult.AuthFailure))
        assertEquals(listOf("cancel"), scheduler.calls)
        assertEquals(1, notifier.connectionProblems)
    }
}
