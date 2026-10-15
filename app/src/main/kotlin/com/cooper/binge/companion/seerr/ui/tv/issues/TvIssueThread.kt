package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.component.TvConfirmDialog
import com.binge.designsystem.tv.component.TvIconButton
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.component.TvSideSheetNav
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetRows
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.binge.designsystem.tv.focus.tvStartDirectionKey
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.issues.IssueAction
import com.cooper.binge.companion.seerr.ui.issues.IssueDetail
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.issueAffectedLabel
import com.cooper.binge.companion.seerr.ui.issues.labelRes
import com.cooper.binge.companion.seerr.ui.tv.TvActionRun
import com.binge.designsystem.R as DesR
import com.cooper.binge.companion.seerr.ui.requests.labelRes as mediaTypeLabelRes

/** The report's card: the issue's own first entry, keyed apart from the comments. */
internal const val KEY_REPORT = "report"
internal const val KEY_REPLY = "reply"
private const val KEY_TOGGLE = "toggle"
private const val KEY_OPEN = "open"
private const val KEY_BINGE = "binge"
private const val KEY_DELETE = "delete"

internal fun commentKey(commentId: Int) = "comment:$commentId"

/**
 * The sheet's first page: the title, the report as a card, the issue's actions as a centred row of round buttons, the
 * reply hand-off, then the comments as cards. Focus arrives on the report, or on [arrival] when the thread shows again
 * after a comment was deleted. Left steps back along the button row and closes the sheet only from its first button;
 * anywhere else on the page Left closes the sheet.
 */
@Composable
internal fun ColumnScope.TvIssueThread(
    state: IssueDetailUiState.Ready,
    nav: TvSideSheetNav,
    actions: TvIssueSheetActions,
    run: TvActionRun,
    arrival: String?,
) {
    val detail = state.detail
    val item = detail.item
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmingDelete) {
        TvConfirmDialog(
            title = stringResource(R.string.issue_delete_confirm_title),
            message = stringResource(R.string.issue_delete_confirm_message),
            confirmLabel = stringResource(R.string.issue_delete),
            onConfirm = {
                confirmingDelete = false
                run.run { actions.onDeleteIssue() }
            },
            onDismissRequest = { confirmingDelete = false },
            destructive = true,
        )
    }
    val report = reportCard(state)
    TvSideSheetTitle(item.title ?: stringResource(item.mediaType.mediaTypeLabelRes()))
    TvSideSheetRows {
        report?.let { card ->
            TvIssueCard(
                card = card,
                onClick = { nav.push(issueCardPage(KEY_REPORT), openedBy = KEY_REPORT) },
                modifier = nav.row(KEY_REPORT),
            )
        }
        IssueButtons(detail, state.action, nav, actions, onToggle = { run.run { actions.onToggleStatus() } }) {
            confirmingDelete = true
        }
        TvSaveStatus(status = run.status, onRetry = run::again)
        if (detail.canComment) {
            TvSideSheetRow(
                label = stringResource(R.string.tv_issue_reply_on_phone),
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = { nav.push(ISSUE_PAGE_PHONE, openedBy = KEY_REPLY) },
                modifier = nav.row(KEY_REPLY),
                opensPage = true,
            )
        }
        detail.comments.forEach { comment ->
            val key = commentKey(comment.id)
            TvIssueCard(
                card = commentCard(comment, state.now),
                onClick = { nav.push(issueCardPage(key), openedBy = key) },
                modifier = nav.row(key),
            )
        }
    }
    // Only a key the page drew can take focus: a report-less issue, or a deleted comment's neighbour gone too, leaves the first row.
    val drawn =
        listOfNotNull(KEY_REPORT.takeIf { report != null }, KEY_REPLY.takeIf { detail.canComment }) +
            detail.comments.map { commentKey(it.id) }
    (arrival?.takeIf { it in drawn } ?: KEY_REPORT.takeIf { report != null })?.let(nav::arriveAt)
}

/**
 * The issue's actions, centred: resolve or reopen, open the title in the media server's TV app or in Binge, each only where
 * it would open, and delete, last and behind a confirm. Left from any button but the first steps to the one before it, rather than closing the sheet.
 */
@Composable
private fun IssueButtons(
    detail: IssueDetail,
    action: IssueAction,
    nav: TvSideSheetNav,
    actions: TvIssueSheetActions,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val start = tvStartDirectionKey()
    val back = if (start == Key.DirectionLeft) FocusDirection.Left else FocusDirection.Right
    val busy = action != IssueAction.None
    val openLabel = detail.mediaServerName?.let { stringResource(R.string.open_in_named, it) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s), Alignment.CenterHorizontally),
        // A Left that moved focus is the row's; one that found nothing before the first button is left for the sheet to close on.
        modifier =
            Modifier.fillMaxWidth().onKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == start && focusManager.moveFocus(back)
            },
    ) {
        if (detail.canResolve) {
            val resolving = detail.item.status == IssueStatus.Open
            TvIconButton(
                icon = if (resolving) Icons.Filled.CheckCircle else Icons.Filled.Refresh,
                label = stringResource(if (resolving) R.string.issue_action_resolve else R.string.issue_action_reopen),
                onClick = onToggle,
                loading = action == IssueAction.UpdatingStatus,
                enabled = !busy || action == IssueAction.UpdatingStatus,
                modifier = nav.row(KEY_TOGGLE),
            )
        }
        val onOpenInMediaServer = actions.onOpenInMediaServer
        if (onOpenInMediaServer != null && openLabel != null) {
            TvIconButton(icon = Icons.Filled.PlayArrow, label = openLabel, onClick = onOpenInMediaServer, modifier = nav.row(KEY_OPEN))
        }
        actions.onOpenInBinge?.let { open ->
            TvIconButton(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                label = stringResource(R.string.request_open_binge),
                onClick = open,
                modifier = nav.row(KEY_BINGE),
            )
        }
        if (detail.canDelete) {
            TvIconButton(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.issue_delete),
                onClick = onDelete,
                style = TvButtonStyle.DestructiveOutline,
                loading = action == IssueAction.Deleting,
                enabled = !busy || action == IssueAction.Deleting,
                modifier = nav.row(KEY_DELETE),
            )
        }
    }
}

/** The report as a card: its author and age, what kind of problem and where, and what they wrote; null with nothing to show. */
@Composable
internal fun reportCard(state: IssueDetailUiState.Ready): TvIssueCardContent? {
    val detail = state.detail
    val item = detail.item
    val text = (detail.report?.message ?: item.problem)?.takeIf { it.isNotBlank() } ?: return null
    val separator = stringResource(R.string.hub_meta_separator)
    return TvIssueCardContent(
        author = item.reportedBy.orEmpty(),
        avatarUrl = item.reportedByAvatarUrl,
        meta = listOfNotNull(item.reportedBy, formatRelativeOrAbsolute(item.createdAtMillis, state.now)).joinToString(separator),
        kind =
            listOfNotNull(
                stringResource(item.type.labelRes()),
                issueAffectedLabel(item),
                stringResource(item.status.labelRes()),
            ).joinToString(separator),
        message = text,
        isMine = detail.report?.isMine == true,
    )
}
