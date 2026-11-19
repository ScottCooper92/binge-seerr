package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeFloatingComposer
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The card floating over the thread: an inline field for a comment, Resolve or Reopen as a chip where the user may, and Send.
 * A status change in flight holds the field, keeping the keyboard up, and shows a spinner in the chip's place, so the two
 * cannot race. Where the user may not comment there is no card. The card's height comes back through [onOccupiedHeightChange] for the thread to
 * end above it.
 */
@Composable
internal fun IssueComposer(
    detail: IssueDetail,
    action: IssueAction,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPostComment: () -> Unit,
    onToggleStatus: () -> Unit,
    onOccupiedHeightChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = action == IssueAction.None
    val chip: (@Composable RowScope.() -> Unit)? =
        if (detail.canResolve) {
            { StatusChip(detail, action, onToggleStatus) }
        } else {
            null
        }
    if (detail.canComment) {
        BingeFloatingComposer(
            value = draft,
            onValueChange = onDraftChange,
            onSend = { if (idle) onPostComment() },
            placeholder = stringResource(R.string.issue_comment_hint),
            sendDescription = stringResource(R.string.issue_comment_send),
            modifier = modifier,
            leadingActions = chip,
            readOnly = !idle,
            onOccupiedHeightChange = onOccupiedHeightChange,
        )
    }
}

/** Resolve or Reopen, which spins while the change is in flight. */
@Composable
private fun RowScope.StatusChip(
    detail: IssueDetail,
    action: IssueAction,
    onToggleStatus: () -> Unit,
) {
    val resolving = detail.item.status == IssueStatus.Open
    if (action == IssueAction.UpdatingStatus) {
        CircularProgressIndicator(
            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_s)).size(dimensionResource(DesR.dimen.padding_l)),
            strokeWidth = dimensionResource(DesR.dimen.progress_stroke_width),
        )
    } else {
        BingeFilterChip(
            label = stringResource(if (resolving) R.string.issue_action_resolve else R.string.issue_action_reopen),
            selected = false,
            onClick = onToggleStatus,
            enabled = action == IssueAction.None,
        )
    }
}
