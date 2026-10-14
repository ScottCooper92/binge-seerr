package com.cooper.binge.companion.seerr.ui.tv.settings

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import org.junit.Assert.assertEquals
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
}
