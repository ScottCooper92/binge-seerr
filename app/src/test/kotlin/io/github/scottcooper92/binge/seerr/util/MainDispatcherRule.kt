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
 * Ten files keep their own `setMain` with no reset, and the reason is not the real socket they
 * drive: twenty-five other files drive a `MockWebServer` through this rule. Two separate causes,
 * both since measured (#177).
 *
 * `HubViewModelTest` hangs, because it turns on `DownloadsPoller`, whose loop is
 * `while (true) { …; delay(…) }` - a virtual clock drives that forever and `runTest` never goes
 * idle. The other nine do not hang; taking Main away exposes work that outlives their teardown,
 * which the leaked dispatcher had been masking. A Retrofit call cancelled by `ViewModelStore.clear`
 * still resumes on OkHttp's thread afterwards, and dispatching that resume to an absent Main
 * throws. Resetting is what makes it visible, so the fix is for nothing to outlive the test - not
 * for Main to stay leaked.
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
