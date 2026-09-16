package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvPreviews
import io.github.scottcooper92.binge.seerr.ui.tv.NoRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.SampleRequests
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadPhase
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.requestsReady
import io.github.scottcooper92.binge.seerr.ui.tv.rows
import kotlinx.coroutines.flow.emptyFlow

/**
 * The television requests board: the loaded list, the moderation sheet open on a row, the empty arm and
 * the failed-load plate. Mirrors the states already sketched in `TvBoardPreviews.kt`.
 */
class TvRequestsScreenshotTest {
    @PreviewTest
    @SeerrTvPreviews
    @Composable
    fun Board() {
        TvRequestsBoard(
            state = requestsReady(),
            rows = rows(SampleRequests),
            events = emptyFlow(),
            actions = NoRequestsActions,
            initialFocusedRowId = 1,
        )
    }

    @PreviewTest
    @SeerrTvPreviews
    @Composable
    fun Sheet() {
        TvRequestsBoard(
            state = requestsReady(actionItem = SampleRequests.first()),
            rows = rows(SampleRequests),
            events = emptyFlow(),
            actions = NoRequestsActions,
        )
    }

    @PreviewTest
    @SeerrTvPreviews
    @Composable
    fun Empty() {
        TvRequestsBoard(state = requestsReady(), rows = rows(emptyList()), events = emptyFlow(), actions = NoRequestsActions)
    }

    @PreviewTest
    @SeerrTvPreviews
    @Composable
    fun Failed() {
        TvRequestsBoard(
            state = requestsReady(),
            rows = TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed(rejected = false)),
            events = emptyFlow(),
            actions = NoRequestsActions,
        )
    }
}
