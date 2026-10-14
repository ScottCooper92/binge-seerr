package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * An issue's sheet over the Issues tab: the thread with its report, its actions and its comments, a resolved issue's
 * Reopen, a write that failed, a card's own page, the phone hand-off, and the loading and failed sheet. The cards
 * themselves, at rest and focused, the user's own and someone else's, are framed apart.
 */
class TvIssueSheetScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Thread() = Sheet(sampleIssueSheetState(FIXED_NOW_MILLIS))

    /** A resolved issue's first action reopens it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ResolvedThread() {
        val state = sampleIssueSheetState(FIXED_NOW_MILLIS)
        Sheet(state.copy(detail = state.detail.copy(item = state.detail.item.copy(status = IssueStatus.Resolved))))
    }

    /** A write the server refused: the line under the actions says so, with a Retry to reach (#1519). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ActionFailed() = Sheet(sampleIssueSheetState(FIXED_NOW_MILLIS), initialStatus = SaveStatus.Failed)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportPage() = Sheet(sampleIssueSheetState(FIXED_NOW_MILLIS), page = issueCardPage(KEY_REPORT))

    /** The user's own comment: Edit on your phone, and Delete comment last. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun OwnCommentPage() = Sheet(sampleIssueSheetState(FIXED_NOW_MILLIS), page = issueCardPage(commentKey(OWN_COMMENT_ID)))

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun PhonePage() = Sheet(sampleIssueSheetState(FIXED_NOW_MILLIS), page = ISSUE_PAGE_PHONE)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() = Sheet(IssueDetailUiState.Loading)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Failed() = Sheet(IssueDetailUiState.Error(SeerrError.Unreachable))

    /** Someone else's card and the user's own, each at rest and focused, as the sheet's panel lays them out. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Cards() {
        val state = sampleIssueSheetState(FIXED_NOW_MILLIS)
        val (theirs, mine) = state.detail.comments
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier =
                    Modifier
                        .width(dimensionResource(TvR.dimen.tv_side_sheet_width))
                        .padding(dimensionResource(DesR.dimen.padding_l)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
            ) {
                TvIssueCard(card = commentCard(theirs, FIXED_NOW_MILLIS))
                TvIssueCard(card = commentCard(theirs, FIXED_NOW_MILLIS), initiallyFocused = true)
                TvIssueCard(card = commentCard(mine, FIXED_NOW_MILLIS))
                TvIssueCard(card = commentCard(mine, FIXED_NOW_MILLIS), initiallyFocused = true)
            }
        }
    }
}

private const val OWN_COMMENT_ID = 102

@Composable
private fun Sheet(
    state: IssueDetailUiState,
    page: String = ISSUE_PAGE_THREAD,
    initialStatus: SaveStatus = SaveStatus.Idle,
) {
    TvIssueSheet(state = state, actions = NoIssueSheetActions, initialPage = page, initialStatus = initialStatus)
}
