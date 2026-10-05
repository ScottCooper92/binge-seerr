package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import com.binge.designsystem.template.PagedPhase
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * A paged list's refresh as the boards read it. The decision is the phone's [PagedPhase]; this is only how
 * a board shows it. A mediator's failure behind cached rows is the one that counts (#625).
 */
class TvRefreshPhaseTest {
    private val idle = LoadState.NotLoading(endOfPaginationReached = false)
    private val failure = IOException("unreachable")

    @Test
    fun `the skeleton is the loading plate and an empty list is idle`() {
        assertEquals(TvLoadPhase.Loading, PagedPhase.Skeleton.tvRefresh())
        assertEquals(TvLoadPhase.Idle, PagedPhase.Empty.tvRefresh())
    }

    @Test
    fun `a failed refresh behind rows fails, a running one loads, and a quiet one is idle`() {
        assertEquals(TvLoadPhase.Failed(rejected = false), PagedPhase.Rows(refreshing = false, refreshError = failure).tvRefresh())
        assertEquals(TvLoadPhase.Loading, PagedPhase.Rows(refreshing = true, refreshError = null).tvRefresh())
        assertEquals(TvLoadPhase.Idle, PagedPhase.Rows(refreshing = false, refreshError = null).tvRefresh())
    }

    @Test
    fun `a first load the server rejected offers to reconnect`() {
        assertEquals(
            TvLoadPhase.Failed(rejected = true),
            PagedPhase.Failed(HttpException(Response.error<Unit>(401, "{}".toResponseBody()))).tvRefresh(),
        )
        assertEquals(TvLoadPhase.Failed(rejected = false), PagedPhase.Failed(failure).tvRefresh())
    }

    @Test
    fun `the append reads the mediator, and its error outranks the source`() {
        val states =
            CombinedLoadStates(
                refresh = idle,
                prepend = idle,
                append = idle,
                source = LoadStates(refresh = idle, prepend = idle, append = LoadState.Loading),
                mediator = LoadStates(refresh = idle, prepend = idle, append = LoadState.Error(failure)),
            )

        assertEquals(TvLoadPhase.Failed(rejected = false), states.appendPhase())
    }
}
