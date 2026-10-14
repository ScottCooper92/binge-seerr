package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.tv.NoIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.TvLoadPhase
import com.cooper.binge.companion.seerr.ui.tv.TvPagedRows
import com.cooper.binge.companion.seerr.ui.tv.issue
import com.cooper.binge.companion.seerr.ui.tv.issuesReady
import com.cooper.binge.companion.seerr.ui.tv.rows

private val FixedSampleIssues =
    listOf(
        issue(11, "Severance", IssueStatus.Open, now = FIXED_NOW_MILLIS),
        issue(12, "Slow Horses", IssueStatus.Resolved, now = FIXED_NOW_MILLIS),
    )

/**
 * The television issues board as the design system's immersive hub: the open and resolved issues as rows over the
 * backdrop, the empty arm and the failed-load page. Mirrors the states sketched in `TvBoardPreviewData.kt`; an issue's
 * own sheet is framed by `TvIssueSheetScreenshotTest`.
 */
class TvIssuesScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Board() {
        TvIssuesBoard(
            state = issuesReady(),
            rowsFor = { filter ->
                when (filter) {
                    IssueFilter.Open -> rows(listOf(FixedSampleIssues[0]))
                    IssueFilter.Resolved -> rows(listOf(FixedSampleIssues[1]))
                    else -> rows(emptyList())
                }
            },
            actions = NoIssuesActions,
            now = FIXED_NOW_MILLIS,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Empty() {
        TvIssuesBoard(state = issuesReady(), rowsFor = { rows(emptyList()) }, actions = NoIssuesActions)
    }

    /** A load that failed, a rejected one included: the page says so, with a retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun FailedLoad() {
        TvIssuesBoard(
            state = issuesReady(),
            rowsFor = { TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed) },
            actions = NoIssuesActions,
        )
    }

    /** Before the viewer's scope is known (#1053): no rows yet, so the loading page. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScopeLoading() {
        TvIssuesBoard(state = IssuesUiState.Loading, rowsFor = { rows(emptyList()) }, actions = NoIssuesActions)
    }

    /** The scope could not be read: the page says so, and its retry reads the scope again. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScopeFailed() {
        TvIssuesBoard(
            state = IssuesUiState.Error(SeerrError.Unreachable),
            rowsFor = { rows(emptyList()) },
            actions = NoIssuesActions,
        )
    }
}
