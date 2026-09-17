package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.tv.NoRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.SampleRequests
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadPhase
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.requestsReady
import io.github.scottcooper92.binge.seerr.ui.tv.rows

/**
 * The television requests board: the loaded list, the empty arm and the failed-load plate. A row's own
 * moderation sheet is framed on `TvRequestDetailScreenshotTest` now, off the detail page that owns it.
 * Mirrors the states already sketched in `TvBoardPreviews.kt`.
 */
class TvRequestsScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Board() {
        TvRequestsBoard(
            state = requestsReady(),
            rows = rows(SampleRequests),
            actions = NoRequestsActions,
            initialFocusedRowId = 1,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Empty() {
        TvRequestsBoard(state = requestsReady(), rows = rows(emptyList()), actions = NoRequestsActions)
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Failed() {
        TvRequestsBoard(
            state = requestsReady(),
            rows = TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed(rejected = false)),
            actions = NoRequestsActions,
        )
    }
}
