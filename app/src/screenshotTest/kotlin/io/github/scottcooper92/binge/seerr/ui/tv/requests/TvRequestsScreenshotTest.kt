package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDownload
import io.github.scottcooper92.binge.seerr.ui.tv.NoRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadPhase
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.request
import io.github.scottcooper92.binge.seerr.ui.tv.requestsReady
import io.github.scottcooper92.binge.seerr.ui.tv.rows

/**
 * A fixed, far-in-the-past render instant for the rows below. `SampleRequests`' own dates are `id` hours
 * before whichever instant the suite runs at, which currently stays inside `formatRelativeOrAbsolute`'s
 * relative-date window regardless — but that's incidental, not by design, the same latent shape that made
 * `TvIssuesScreenshotTest`'s day-scale dates drift a baseline out of sync with the day CI happened to run
 * on (#362). Anchoring both the items and the render instant here keeps the rendered date text invariant
 * rather than merely lucky.
 */
private const val REQUESTS_NOW_MILLIS = 1_770_000_000_000L

private val FixedSampleRequests =
    listOf(
        request(1, "Heat", SeerrRequestStatusCode.Pending, now = REQUESTS_NOW_MILLIS),
        request(
            2,
            "The Bear",
            SeerrRequestStatusCode.Approved,
            seasons = listOf(1, 2),
            mediaStatus = SeerrMediaStatusCode.Processing,
            download = RequestDownload(0.4f, 12, true),
            now = REQUESTS_NOW_MILLIS,
        ),
        request(3, "Dune: Part Two", SeerrRequestStatusCode.Declined, now = REQUESTS_NOW_MILLIS),
    )

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
            rows = rows(FixedSampleRequests),
            actions = NoRequestsActions,
            initialFocusedRowId = 1,
            now = REQUESTS_NOW_MILLIS,
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
