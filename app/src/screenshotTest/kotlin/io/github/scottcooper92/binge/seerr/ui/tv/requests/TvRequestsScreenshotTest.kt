package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.FIXED_NOW_MILLIS
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDownload
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.tv.NoRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadPhase
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.request
import io.github.scottcooper92.binge.seerr.ui.tv.requestsReady
import io.github.scottcooper92.binge.seerr.ui.tv.rows

private const val SYNOPSIS = "A crew, a cop, and a city that does not remember either of them, until the pressure of the job moves on."

private val FixedSampleRequests =
    listOf(
        request(1, "Heat", SeerrRequestStatusCode.Pending, now = FIXED_NOW_MILLIS).copy(overview = SYNOPSIS, certification = "15"),
        request(
            2,
            "The Bear",
            SeerrRequestStatusCode.Approved,
            seasons = listOf(1, 2),
            mediaStatus = SeerrMediaStatusCode.Processing,
            download = RequestDownload(0.4f, 12, true),
            now = FIXED_NOW_MILLIS,
        ).copy(overview = SYNOPSIS, certification = "18"),
        request(3, "Dune: Part Two", SeerrRequestStatusCode.Declined, now = FIXED_NOW_MILLIS),
    )

/**
 * The television requests hub: the rows over the backdrop at rest, the empty arm, the loading and failed-load
 * pages, and the failed-scope pages. A request's own moderation is framed on `TvRequestDetailScreenshotTest`.
 * Mirrors the states sketched in `TvBoardPreviewData.kt`.
 */
class TvRequestsScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Board() {
        TvRequestsRowsBoard(
            state = requestsReady(),
            rowsFor = { filter ->
                when (filter) {
                    RequestFilter.Pending -> rows(listOf(FixedSampleRequests[0]))
                    RequestFilter.Approved -> rows(listOf(FixedSampleRequests[1]))
                    RequestFilter.Failed -> rows(listOf(FixedSampleRequests[2]))
                    else -> rows(emptyList())
                }
            },
            actions = NoRequestsActions,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Empty() {
        TvRequestsRowsBoard(state = requestsReady(), rowsFor = { rows(emptyList()) }, actions = NoRequestsActions)
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        TvRequestsRowsBoard(state = RequestsUiState.Loading, rowsFor = { rows(emptyList()) }, actions = NoRequestsActions)
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Failed() {
        TvRequestsRowsBoard(
            state = requestsReady(),
            rowsFor = { TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed) },
            actions = NoRequestsActions,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScopeFailed() {
        TvRequestsRowsBoard(
            state = RequestsUiState.Error(SeerrError.Unreachable),
            rowsFor = { rows(emptyList()) },
            actions = NoRequestsActions,
        )
    }
}
