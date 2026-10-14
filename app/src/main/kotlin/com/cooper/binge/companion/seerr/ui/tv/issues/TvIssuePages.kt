package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvConfirmDialog
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.component.TvSideSheetNav
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetRows
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.issues.IssueDetail
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.tv.TvActionRun
import com.cooper.binge.companion.seerr.ui.tv.TvPhoneCode
import com.cooper.binge.companion.seerr.ui.requests.labelRes as mediaTypeLabelRes

private const val KEY_TEXT = "text"
private const val KEY_EDIT = "edit"
private const val KEY_DELETE_COMMENT = "delete"
private const val KEY_CODE = "code"

/**
 * A report or comment in full, read-only, under the card it opened from: one card per paragraph, so the D-pad reads down a
 * long one. Where the viewer may act on it, Edit on your phone hands off to the issue's page, and a comment, never the
 * report, can be deleted, last and behind a confirm. A card that is gone, deleted here or elsewhere, closes its page; focus
 * then lands on the card after it, else the one before, else the reply row.
 */
@Composable
internal fun ColumnScope.TvIssueCardPage(
    state: IssueDetailUiState.Ready,
    cardKey: String,
    nav: TvSideSheetNav,
    run: TvActionRun,
    onDeleteComment: (commentId: Int, next: String) -> Unit,
) {
    val detail = state.detail
    val isReport = cardKey == KEY_REPORT
    val comment = detail.comments.firstOrNull { commentKey(it.id) == cardKey }
    val card = if (isReport) reportCard(state) else comment?.let { commentCard(it, state.now) }
    if (card == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val canAct = if (isReport) detail.report?.let(detail::canActOn) == true else comment?.let(detail::canActOn) == true
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmingDelete && comment != null) {
        TvConfirmDialog(
            title = stringResource(R.string.issue_delete_comment_title),
            message = stringResource(R.string.issue_delete_comment_message),
            confirmLabel = stringResource(R.string.issue_delete_comment),
            onConfirm = {
                confirmingDelete = false
                onDeleteComment(comment.id, nextAfter(detail, comment.id))
            },
            onDismissRequest = { confirmingDelete = false },
            destructive = true,
        )
    }
    TvSideSheetTitle(detail.item.title ?: stringResource(detail.item.mediaType.mediaTypeLabelRes()))
    TvSideSheetRows {
        card.message.split(PARAGRAPH).filter { it.isNotBlank() }.forEachIndexed { index, paragraph ->
            TvIssueCard(
                card = if (index == 0) card.copy(message = paragraph) else card.copy(message = paragraph, kind = null),
                maxLines = Int.MAX_VALUE,
                modifier = nav.row("$KEY_TEXT$index"),
            )
        }
        if (canAct) {
            TvSideSheetRow(
                label = stringResource(R.string.tv_issue_edit_on_phone),
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = { nav.push(ISSUE_PAGE_PHONE, openedBy = KEY_EDIT) },
                modifier = nav.row(KEY_EDIT),
            )
        }
        if (canAct && comment != null) {
            TvSideSheetRow(
                label = stringResource(R.string.issue_delete_comment),
                icon = Icons.Filled.Delete,
                destructive = true,
                onClick = { confirmingDelete = true },
                modifier = nav.row(KEY_DELETE_COMMENT),
            )
            TvSaveStatus(status = run.status, onRetry = run::again)
        }
    }
}

/** Where focus goes once [commentId] is deleted: the card after it, else the one before, else the reply row. */
internal fun nextAfter(
    detail: IssueDetail,
    commentId: Int,
): String {
    val ids = detail.comments.map { it.id }
    val at = ids.indexOf(commentId)
    return (ids.getOrNull(at + 1) ?: ids.getOrNull(at - 1))?.let(::commentKey) ?: KEY_REPLY
}

/**
 * Replying and editing need a keyboard, which a television does without: the issue's page on the server as a code the
 * phone scans, to reply or edit there.
 */
@Composable
internal fun ColumnScope.TvIssuePhonePage(
    detail: IssueDetail,
    nav: TvSideSheetNav,
) {
    TvSideSheetTitle(detail.item.title ?: stringResource(detail.item.mediaType.mediaTypeLabelRes()))
    TvPhoneCode(url = detail.webUrl, body = stringResource(R.string.tv_issue_phone_body, detail.serverName), modifier = nav.row(KEY_CODE))
}

private val PARAGRAPH = Regex("\n\\s*\n")
