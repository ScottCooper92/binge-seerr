package io.github.scottcooper92.binge.seerr.ui.users.settings

import kotlinx.coroutines.CompletableDeferred
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

    private fun TestScope.saveAsMade() =
        SaveAsMade(
            scope = this,
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
}
