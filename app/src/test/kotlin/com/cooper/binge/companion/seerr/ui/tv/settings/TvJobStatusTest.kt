package com.cooper.binge.companion.seerr.ui.tv.settings

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TvJobStatusTest {
    @Test
    fun `a list that is not read says nothing of a job`() {
        assertEquals(TvJobStatus.Idle, JobsUiState.Loading.statusOf("any"))
        assertEquals(TvJobStatus.Idle, JobsUiState.Error(SeerrError.Server).statusOf("any"))
    }

    @Test
    fun `a job is busy while a call is in flight and failed once one was refused`() {
        val ready = JobsUiState.Ready(jobs = emptyList(), busyIds = setOf("a"), failedIds = setOf("b"))

        assertEquals(TvJobStatus.Busy, ready.statusOf("a"))
        assertEquals(TvJobStatus.Failed, ready.statusOf("b"))
        assertEquals(TvJobStatus.Idle, ready.statusOf("c"))
    }

    @Test
    fun `a refusal is said before anything else, and a call out or a held press reads as working`() {
        assertEquals(TvJobLine.Failed, jobLineOf(running = true, TvJobStatus.Failed, TvJobPress(stopping = false)))
        assertEquals(TvJobLine.Working, jobLineOf(running = false, TvJobStatus.Busy, press = null))
        assertEquals(TvJobLine.Working, jobLineOf(running = true, TvJobStatus.Idle, TvJobPress(stopping = false, holding = true)))
    }

    @Test
    fun `once the hold is over a long job keeps its running line, then says what the press did`() {
        val ran = TvJobPress(stopping = false, holding = false)
        val stopped = TvJobPress(stopping = true, holding = false)

        assertEquals(TvJobLine.Running, jobLineOf(running = true, TvJobStatus.Idle, ran))
        assertEquals(TvJobLine.Done, jobLineOf(running = false, TvJobStatus.Idle, ran))
        assertEquals(TvJobLine.Stopped, jobLineOf(running = false, TvJobStatus.Idle, stopped))
    }

    @Test
    fun `a job nobody pressed says when it runs`() {
        assertEquals(TvJobLine.Schedule, jobLineOf(running = false, TvJobStatus.Idle, press = null))
        assertEquals(TvJobLine.Running, jobLineOf(running = true, TvJobStatus.Idle, press = null))
    }

    @Test
    fun `a press is held until it is released, and released only if it was pressed`() {
        val presses = TvJobPresses()
        presses.release("a")
        assertEquals(null, presses["a"])

        presses.press("a", stopping = true)
        assertEquals(TvJobPress(stopping = true, holding = true), presses["a"])
        presses.release("a")
        assertEquals(TvJobPress(stopping = true, holding = false), presses["a"])
    }

    @Test
    fun `a second press while the first is held is a new press, so its hold starts again`() {
        val presses = TvJobPresses()
        presses.press("a", stopping = false)
        val first = presses["a"]

        presses.press("a", stopping = false)

        assertNotEquals(first, presses["a"])
        assertEquals(true, presses["a"]?.holding)
    }
}
