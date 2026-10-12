package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueListEvent
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.tv.NoIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.TvLoadPhase
import com.cooper.binge.companion.seerr.ui.tv.TvPagedRows
import com.cooper.binge.companion.seerr.ui.tv.issue
import com.cooper.binge.companion.seerr.ui.tv.issuesReady
import com.cooper.binge.companion.seerr.ui.tv.rows
import kotlinx.coroutines.flow.emptyFlow

private val FixedSampleIssues =
    listOf(
        issue(11, "Severance", IssueStatus.Open, now = FIXED_NOW_MILLIS),
        issue(12, "Slow Horses", IssueStatus.Resolved, now = FIXED_NOW_MILLIS),
    )

/**
 * The television issues board as the design system's immersive hub: the open and resolved issues as rows over the
 * backdrop, the actions sheet open on a card, the empty arm and the failed-load page. Mirrors the states sketched
 * in `TvBoardPreviewData.kt`.
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
            events = emptyFlow(),
            actions = NoIssuesActions,
            now = FIXED_NOW_MILLIS,
        )
    }

    /** A resolve that landed: the note at the foot of the board, until it clears a few seconds later (#1053). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ResolvedNote() {
        TvIssuesBoard(
            state = issuesReady(),
            rowsFor = { filter ->
                when (filter) {
                    IssueFilter.Open -> rows(listOf(FixedSampleIssues[0]))
                    IssueFilter.Resolved -> rows(listOf(FixedSampleIssues[1]))
                    else -> rows(emptyList())
                }
            },
            events = emptyFlow(),
            actions = NoIssuesActions,
            now = FIXED_NOW_MILLIS,
            initialEvent = IssueListEvent.Resolved,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Sheet() {
        TvIssuesBoard(
            state = issuesReady(actionItem = FixedSampleIssues.first()),
            rowsFor = { filter -> if (filter == IssueFilter.Open) rows(listOf(FixedSampleIssues[0])) else rows(emptyList()) },
            events = emptyFlow(),
            actions = NoIssuesActions,
            now = FIXED_NOW_MILLIS,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Empty() {
        TvIssuesBoard(state = issuesReady(), rowsFor = { rows(emptyList()) }, events = emptyFlow(), actions = NoIssuesActions)
    }

    /** A load that failed, a rejected one included: the page says so, with a retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun FailedLoad() {
        TvIssuesBoard(
            state = issuesReady(),
            rowsFor = { TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed) },
            events = emptyFlow(),
            actions = NoIssuesActions,
        )
    }

    /** Before the viewer's scope is known (#1053): no rows yet, so the loading page. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScopeLoading() {
        TvIssuesBoard(state = IssuesUiState.Loading, rowsFor = { rows(emptyList()) }, events = emptyFlow(), actions = NoIssuesActions)
    }

    /** The scope could not be read: the page says so, and its retry reads the scope again. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScopeFailed() {
        TvIssuesBoard(
            state = IssuesUiState.Error(SeerrError.Unreachable),
            rowsFor = { rows(emptyList()) },
            events = emptyFlow(),
            actions = NoIssuesActions,
        )
    }

    /** A resolved issue's sheet (#1053): its first action reopens it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ResolvedSheet() {
        TvIssuesBoard(
            state = issuesReady(actionItem = FixedSampleIssues[1]),
            rowsFor = { filter -> if (filter == IssueFilter.Resolved) rows(listOf(FixedSampleIssues[1])) else rows(emptyList()) },
            events = emptyFlow(),
            actions = NoIssuesActions,
            now = FIXED_NOW_MILLIS,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ResolveConfirm() = ConfirmFrame(FixedSampleIssues[0], TvIssueConfirm.Resolve)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReopenConfirm() = ConfirmFrame(FixedSampleIssues[1], TvIssueConfirm.Reopen)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun DeleteConfirm() = ConfirmFrame(FixedSampleIssues[0], TvIssueConfirm.Delete)
}

/** The sheet on one of its confirm steps, alone: the board behind it is framed by [TvIssuesScreenshotTest.Sheet]. */
@Composable
private fun ConfirmFrame(
    item: IssueItem,
    confirm: TvIssueConfirm,
) {
    TvIssueActionsSheet(
        item = item,
        canDelete = true,
        onResolve = {},
        onReopen = {},
        onDelete = {},
        onOpenDetail = {},
        onDismiss = {},
        initialConfirm = confirm,
    )
}
