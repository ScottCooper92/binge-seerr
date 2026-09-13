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
 * The ten test files that drive a real `MockWebServer` do not use this yet and keep their own
 * `setMain` with no reset. A coroutine suspended on a real socket resumes on OkHttp's thread
 * whatever Main is, so a virtual clock hangs them rather than steadying them - and taking Main
 * away at all exposes work that outlives their teardown, which the leaked dispatcher had been
 * masking. Both are #177.
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
