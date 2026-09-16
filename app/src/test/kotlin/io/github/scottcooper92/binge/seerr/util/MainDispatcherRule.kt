package io.github.scottcooper92.binge.seerr.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces `Dispatchers.Main` for the life of one test and puts it back afterwards.
 *
 * The reset is the part every test needs: without it a replaced Main leaks into whatever runs
 * next, and a rule cannot forget to do it.
 *
 * The default dispatcher is a [TestDispatcher], which is the part that matters for determinism.
 * `UnconfinedTestDispatcher()` adopts the scheduler of whatever Main is mocked with, so a
 * dispatcher created later in the test body shares one virtual clock with `viewModelScope`. Set
 * Main to something that is not a `TestDispatcher` and every later dispatcher gets a scheduler of
 * its own: `delay()` in production code really sleeps, `advanceUntilIdle()` has nothing to
 * advance, and `runTest` cannot tell when the ViewModel's work has finished — which pushes a test
 * towards sampling state instead of awaiting it, and sampling is where the races live.
 *
 * Three files still keep their own `setMain` with no reset (#177): a real `MockWebServer` call
 * cancelled by `ViewModelStore.clear` can still resume on OkHttp's own thread afterwards, and
 * dispatching that resume to an absent Main throws — resetting is what makes it visible, not what
 * causes it. Every other file that drives a `MockWebServer` takes this rule safely instead, by
 * giving `SeerrApiFactory` (or `plexTvApi`) [synchronousDispatcher] in place of OkHttp's own thread
 * pool: a call resumes its coroutine inline, with nothing left to outlive the test. That is not
 * available to the three left:
 *
 * - `HubViewModelTest` turns on `DownloadsPoller`, whose loop is `while (true) { …; delay(…) }` — a
 *   virtual clock drives that forever and `runTest` never goes idle, same-thread or not.
 * - `IssueDetailViewModelTest` and `RequestDetailViewModelTest` each hold a response back with a
 *   blocking `CountDownLatch` so a second, overlapping action can run while the first is still in
 *   flight (mid-send cancellation). A same-thread dispatcher would block the test's own thread for
 *   the held call, so the later `countDown()` that is supposed to release it would never run.
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
