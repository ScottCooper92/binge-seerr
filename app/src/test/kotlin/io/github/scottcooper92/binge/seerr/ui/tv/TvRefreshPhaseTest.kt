package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** A paged list's refresh as the boards read it: the mediator's failure behind cached rows is the one that counts (#625). */
class TvRefreshPhaseTest {
    private val idle = LoadState.NotLoading(endOfPaginationReached = false)
    private val failure = LoadState.Error(IOException("unreachable"))

    private fun states(
        refresh: LoadState,
        mediatorRefresh: LoadState? = null,
        mediatorAppend: LoadState = idle,
        append: LoadState = idle,
    ) = CombinedLoadStates(
        refresh = refresh,
        prepend = idle,
        append = append,
        source = LoadStates(refresh = refresh, prepend = idle, append = append),
        mediator = mediatorRefresh?.let { LoadStates(refresh = it, prepend = idle, append = mediatorAppend) },
    )

    @Test
    fun `a failed network refresh behind a finished database read is a failed refresh`() {
        val phase = states(refresh = idle, mediatorRefresh = failure).refreshPhase()

        assertEquals(TvLoadPhase.Failed(rejected = false), phase)
    }

    @Test
    fun `a failure on the source alone still fails`() {
        assertEquals(TvLoadPhase.Failed(rejected = false), states(refresh = failure).refreshPhase())
    }

    @Test
    fun `a mediator still loading is loading, and a quiet one is idle`() {
        assertEquals(TvLoadPhase.Loading, states(refresh = idle, mediatorRefresh = LoadState.Loading).refreshPhase())
        assertEquals(TvLoadPhase.Idle, states(refresh = idle, mediatorRefresh = idle).refreshPhase())
    }

    @Test
    fun `an error outranks a load in progress on the other side`() {
        assertEquals(TvLoadPhase.Failed(rejected = false), states(refresh = LoadState.Loading, mediatorRefresh = failure).refreshPhase())
    }

    @Test
    fun `the append reads the mediator the same way`() {
        val phase = states(refresh = idle, mediatorRefresh = idle, mediatorAppend = failure).appendPhase()

        assertEquals(TvLoadPhase.Failed(rejected = false), phase)
    }
}
