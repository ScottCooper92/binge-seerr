package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.FixedSampleRequests
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.tv.NoRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.TvLoadPhase
import com.cooper.binge.companion.seerr.ui.tv.TvPagedRows
import com.cooper.binge.companion.seerr.ui.tv.requestsReady
import com.cooper.binge.companion.seerr.ui.tv.rows

private const val SYNOPSIS = "A crew, a cop, and a city that does not remember either of them, until the pressure of the job moves on."

private val SampleRequests =
    listOf(
        FixedSampleRequests[0].copy(overview = SYNOPSIS, certification = "15"),
        FixedSampleRequests[1].copy(overview = SYNOPSIS, certification = "18"),
        FixedSampleRequests[2],
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
                    RequestFilter.Pending -> rows(listOf(SampleRequests[0]))
                    RequestFilter.Approved -> rows(listOf(SampleRequests[1]))
                    RequestFilter.Failed -> rows(listOf(SampleRequests[2]))
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

    /** The server is on the local network and Android has not let this app reach it: Allow, as the hub offers, not a retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScopeLocalNetworkDenied() {
        TvRequestsRowsBoard(
            state = RequestsUiState.Error(SeerrError.LocalNetworkDenied),
            rowsFor = { rows(emptyList()) },
            actions = NoRequestsActions,
        )
    }

    /** A filter with more than a row holds (#1053): its row ends in the See all tile, which opens the grid. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun PastTheRowCap() {
        TvRequestsRowsBoard(
            state = requestsReady().copy(counts = RequestCounts(total = 57, pending = 57, approved = 0, processing = 0, available = 0)),
            rowsFor = { filter -> if (filter == RequestFilter.Pending) rows(SampleRequests) else rows(emptyList()) },
            actions = NoRequestsActions,
        )
    }

    /** The signed-in user's own requests are a row of their own on Home: here the only one, as for a server that lists nothing else. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun YourRequests() {
        TvRequestsRowsBoard(
            state = requestsReady(),
            rowsFor = { rows(emptyList()) },
            mine = TvMyRequests(rows = rows(SampleRequests), count = SampleRequests.size, onSeeAll = {}),
            actions = NoRequestsActions,
        )
    }

    /** More of their own than the row holds: the heading carries the whole count and the row ends in a See all tile (#1043). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun YourRequestsPastTheRowCap() {
        TvRequestsRowsBoard(
            state = requestsReady(),
            rowsFor = { rows(emptyList()) },
            mine = TvMyRequests(rows = rows(SampleRequests), count = 57, onSeeAll = {}),
            actions = NoRequestsActions,
        )
    }
}
