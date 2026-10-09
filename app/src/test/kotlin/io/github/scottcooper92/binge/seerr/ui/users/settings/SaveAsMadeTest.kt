package io.github.scottcooper92.binge.seerr.ui.users.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveAsMadeTest {
    private val sent = mutableListOf<Int>()
    private val gates = mutableListOf<CompletableDeferred<Int>>()
    private var draft = 0
    private var saved = 0

    private fun TestScope.saveAsMade(scope: CoroutineScope = this) =
        SaveAsMade(
            scope = scope,
            appScope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            draft = { draft },
            canSave = { true },
            write = { value ->
                sent += value
                CompletableDeferred<Int>().also { gates += it }.await()
            },
            adopt = { sentValue, adopted ->
                saved = adopted
                if (draft == sentValue) draft = adopted
                draft != adopted
            },
            failed = {},
        )

    @Test
    fun `a change made during a write waits for it, then goes out from the answer`() =
        runTest {
            val mode = saveAsMade()
            draft = 1
            mode.changed()
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()
            assertEquals(listOf(1), sent)

            draft = 2
            mode.changed()
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS * 3)
            runCurrent()
            assertEquals("nothing overlaps the write in flight", listOf(1), sent)

            gates[0].complete(10)
            runCurrent()
            assertEquals("the server's answer is adopted", 10, saved)
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()
            assertEquals("exactly one more write, with the newer draft", listOf(1, 2), sent)
            gates[1].complete(2)
            runCurrent()
            assertEquals(2, gates.size)
        }

    @Test
    fun `settling sends a change still waiting out its delay at once, and only once`() =
        runTest {
            val mode = saveAsMade()
            draft = 1
            mode.changed()

            val settled = mode.settle()
            runCurrent()
            assertEquals(listOf(1), sent)
            gates[0].complete(1)
            runCurrent()
            assertEquals("the change landed, so nothing is kept", null, settled.await())

            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS * 3)
            runCurrent()
            assertEquals("the cancelled wait sends nothing more", listOf(1), sent)
        }

    @Test
    fun `settling waits for the write in flight, then sends the change made during it`() =
        runTest {
            val mode = saveAsMade()
            draft = 1
            mode.changed()
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()
            draft = 2
            mode.changed()

            val settled = mode.settle()
            runCurrent()
            assertEquals("nothing overlaps the write in flight", listOf(1), sent)
            gates[0].complete(1)
            runCurrent()
            assertEquals(listOf(1, 2), sent)
            gates[1].complete(2)
            assertEquals(null, settled.await())

            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS * 3)
            runCurrent()
            assertEquals("the first write's answer schedules no third", listOf(1, 2), sent)
        }

    @Test
    fun `settling with nothing owed waits for the write in flight and sends nothing`() =
        runTest {
            val mode = saveAsMade()
            draft = 1
            mode.changed()
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()

            val settled = mode.settle()
            runCurrent()
            assertEquals(false, settled.isCompleted)
            gates[0].complete(1)
            assertEquals(null, settled.await())
            assertEquals(listOf(1), sent)
        }

    @Test
    fun `settling retries a write that failed, and answers the draft if it fails again`() =
        runTest {
            val mode = saveAsMade()
            draft = 1
            mode.changed()
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()
            gates[0].completeExceptionally(IllegalStateException("offline"))
            runCurrent()

            val settled = mode.settle()
            runCurrent()
            assertEquals(listOf(1, 1), sent)
            gates[1].completeExceptionally(IllegalStateException("still offline"))
            assertEquals("the page keeps the unsent draft", 1, settled.await())
        }

    @Test
    fun `leaving after the scope is cancelled still waits for the write in flight`() =
        runTest {
            val viewModelScope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val mode = saveAsMade(scope = viewModelScope)
            draft = 1
            mode.changed()
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()
            draft = 2
            mode.changed()

            viewModelScope.cancel()
            runCurrent()
            mode.cleared()
            runCurrent()
            assertEquals("the leftover change waits for the write in flight", listOf(1), sent)

            gates[0].complete(1)
            runCurrent()
            assertEquals(listOf(1, 2), sent)
            gates[1].complete(2)
            runCurrent()
        }
}
