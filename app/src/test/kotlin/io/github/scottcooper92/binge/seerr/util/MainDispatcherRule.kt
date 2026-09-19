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
 * Every file in the module takes this rule now (#337). The ten that used to be exempted - the
 * ones driving a real `MockWebServer` - moved to an in-memory transport ([FakeSeerrServer])
 * instead: `HubViewModelTest`'s hang on `DownloadsPoller`'s unbounded loop is gone (it takes a
 * test-visible `DownloadsPollerTicker` now), and the other nine no longer resume a Retrofit call
 * on a real socket's thread after teardown. OkHttp still answers each call on its own thread even
 * against the in-memory fake, though, so a call a test never awaits directly - a fire-and-forget
 * `launch` - could still be resuming when this rule reset Main. Each of the ten files' teardown
 * now calls `FakeSeerrServer.awaitIdle` after clearing its view models: it drains every OkHttp
 * dispatcher a test built before this rule's `finished()` runs, so that resumption always finds a
 * live Main to land on rather than racing its reset (#177).
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
