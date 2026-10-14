package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetNav
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetRows
import com.binge.designsystem.tv.component.TvSideSheetStack
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailEvent
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.tv.TvActionRun
import com.cooper.binge.companion.seerr.ui.tv.rememberTvActionRun
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Everything the issue sheet can ask of its host, in one place so the entry stays a wiring. */
internal class TvIssueSheetActions(
    val onDismiss: () -> Unit,
    val onRetry: () -> Unit,
    val onToggleStatus: () -> Unit,
    val onDeleteIssue: () -> Unit,
    val onDeleteComment: (commentId: Int) -> Unit,
    /** Opens the title in the media server's TV app; null where there is no app to open it, or it is not installed. */
    val onOpenInMediaServer: (() -> Unit)? = null,
    /** Opens the title in Binge; null where Binge would not answer, since there is no browser to fall back to. */
    val onOpenInBinge: (() -> Unit)? = null,
)

/** The sheet's first page: the report, the issue's actions and the comments. */
internal const val ISSUE_PAGE_THREAD = "thread"

/** The page that hands replying or editing to the phone, as a code it scans. */
internal const val ISSUE_PAGE_PHONE = "phone"

/** A card's page is named for the card that opened it, so the page knows what it shows. */
internal fun issueCardPage(cardKey: String) = "page:$cardKey"

/**
 * One issue as a side sheet over the Issues tab, read as a television comment thread: the report as a card, the issue's
 * actions as a row of round buttons, a hand-off to reply on the phone, then the comments as cards. Any card opens its whole
 * text on a page of its own. Replying and editing need a keyboard, so they hand off to the issue's page on the server.
 * Each page returns focus to the card or row that opened it. [initialPage] opens on another page, for a frame.
 */
@Composable
internal fun TvIssueSheet(
    state: IssueDetailUiState,
    actions: TvIssueSheetActions,
    modifier: Modifier = Modifier,
    events: Flow<IssueDetailEvent> = emptyFlow(),
    initialPage: String = ISSUE_PAGE_THREAD,
    initialStatus: SaveStatus = SaveStatus.Idle,
) {
    // A write's answer stands under the action that sent it until the user acts again (#1519).
    val run = rememberTvActionRun(events, initialStatus) { settle(failed = it is IssueDetailEvent.Failed) }
    // Where focus lands when the thread shows again after a comment is deleted: the card after it, else before, else reply.
    var afterDelete by rememberSaveable { mutableStateOf<String?>(null) }
    // Loading and a failure are roots of their own, so the issue arriving starts the thread afresh and focus is offered to
    // the report; on one root, the offer went to the loading line and was not made again once it was gone. A seeded row
    // whose load failed is a failure too: it has nothing to show the thread with, so it offers Retry (#1583).
    val failed = state is IssueDetailUiState.Error || (state is IssueDetailUiState.Seeded && state.error != null)
    val root =
        when {
            state is IssueDetailUiState.Ready -> initialPage
            failed -> PAGE_FAILED
            else -> PAGE_LOADING
        }
    TvSideSheetStack(root = root, onDismissRequest = actions.onDismiss, modifier = modifier, toBottomEdge = true) { nav ->
        when {
            state is IssueDetailUiState.Ready -> IssuePages(state, nav, actions, run, afterDelete) { afterDelete = it }
            failed -> Failed(nav, actions.onRetry)
            else -> Loading(nav)
        }
    }
}

@Composable
private fun ColumnScope.IssuePages(
    state: IssueDetailUiState.Ready,
    nav: TvSideSheetNav,
    actions: TvIssueSheetActions,
    run: TvActionRun,
    afterDelete: String?,
    onAfterDelete: (String) -> Unit,
) {
    val page = nav.page
    when {
        page == ISSUE_PAGE_PHONE -> TvIssuePhonePage(state.detail, nav)
        page.startsWith(issueCardPage("")) ->
            TvIssueCardPage(
                state = state,
                cardKey = page.removePrefix(issueCardPage("")),
                nav = nav,
                run = run,
                onDeleteComment = { commentId, next ->
                    onAfterDelete(next)
                    run.run { actions.onDeleteComment(commentId) }
                },
            )
        else -> TvIssueThread(state, nav, actions, run, arrival = afterDelete)
    }
}

/** The issue is still loading: its title line, and a line that holds focus so the sheet is never left with nowhere to land. */
@Composable
private fun ColumnScope.Loading(nav: TvSideSheetNav) {
    TvSideSheetTitle(stringResource(R.string.issue_detail_title))
    TvSideSheetRows {
        Text(
            text = stringResource(R.string.tv_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = nav.row(KEY_LOADING).focusable(),
        )
    }
}

@Composable
private fun ColumnScope.Failed(
    nav: TvSideSheetNav,
    onRetry: () -> Unit,
) {
    TvSideSheetTitle(stringResource(R.string.issue_detail_title))
    TvSideSheetBody(stringResource(R.string.tv_list_load_failed))
    TvSideSheetRows {
        TvSideSheetRow(
            label = stringResource(R.string.hub_retry),
            icon = Icons.Filled.Refresh,
            onClick = onRetry,
            modifier = nav.row(KEY_RETRY),
        )
    }
}

private const val PAGE_LOADING = "loading"
private const val PAGE_FAILED = "failed"
private const val KEY_LOADING = "loading"
private const val KEY_RETRY = "retry"
