package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvPreviews
import io.github.scottcooper92.binge.seerr.ui.tv.NoIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.SampleIssues
import io.github.scottcooper92.binge.seerr.ui.tv.issuesReady
import io.github.scottcooper92.binge.seerr.ui.tv.rows
import kotlinx.coroutines.flow.emptyFlow

/**
 * The television issues board: the loaded list and the actions sheet open on a row. Mirrors the states
 * already sketched in `TvBoardPreviews.kt`.
 */
class TvIssuesScreenshotTest {
    @PreviewTest
    @SeerrTvPreviews
    @Composable
    fun Board() {
        TvIssuesBoard(
            state = issuesReady(),
            rows = rows(SampleIssues),
            events = emptyFlow(),
            actions = NoIssuesActions,
            initialFocusedRowId = 11,
        )
    }

    @PreviewTest
    @SeerrTvPreviews
    @Composable
    fun Sheet() {
        TvIssuesBoard(
            state = issuesReady(actionItem = SampleIssues.first()),
            rows = rows(SampleIssues),
            events = emptyFlow(),
            actions = NoIssuesActions,
        )
    }
}
