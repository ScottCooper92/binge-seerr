package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/** A bubble stops short of the full width so it stays on its side: the user's own right, everyone else's left. */
private const val BUBBLE_MAX_WIDTH_FRACTION = 0.85f

/** A bubble whose write is in flight, or that the server has not confirmed, reads dimmed. */
private const val DIMMED_ALPHA = 0.6f

/**
 * The thread: the server's comments in order, then the pending ones pinned to the bottom in submit
 * order, so a stuck comment stays visible where it was typed. A long press on a comment the user
 * may act on opens its actions; a plain tap on a pending one opens its retry, edit and drop.
 */
@Composable
internal fun IssueThread(
    thread: List<ThreadEntry>,
    detail: IssueDetail,
    commentAction: CommentAction,
    now: Long,
    onActOn: (IssueComment) -> Unit,
    onTapOutbox: (OutboxComment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing))) {
        if (thread.isEmpty()) {
            Text(
                stringResource(R.string.issue_no_comments),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        thread.forEach { entry ->
            when (entry) {
                is ThreadEntry.Server ->
                    CommentRow(
                        comment = entry.comment,
                        now = now,
                        canAct = detail.canActOn(entry.comment) && commentAction == CommentAction.None,
                        dimmed = commentAction.commentId == entry.comment.id,
                        onAct = { onActOn(entry.comment) },
                    )
                is ThreadEntry.Pending -> OutboxRow(entry.outbox, onTap = { onTapOutbox(entry.outbox) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: IssueComment,
    now: Long,
    canAct: Boolean,
    dimmed: Boolean,
    onAct: () -> Unit,
) {
    CommentColumn(
        isMine = comment.isMine,
        header = { CommentHeader(comment.author, comment.isAdmin, formatRelativeOrAbsolute(comment.createdAtMillis, now)) },
    ) {
        Bubble(
            message = comment.message,
            isMine = comment.isMine,
            dimmed = dimmed,
            modifier =
                if (canAct) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onAct,
                        onLongClickLabel = stringResource(R.string.issue_comment_actions_cd),
                    )
                } else {
                    Modifier
                },
        )
    }
}

@Composable
private fun OutboxRow(
    entry: OutboxComment,
    onTap: () -> Unit,
) {
    CommentColumn(
        isMine = true,
        header = { CommentHeader(entry.author, isAdmin = false, dateLabel = stringResource(R.string.issue_comment_just_now)) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            SendIndicator(entry.state)
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs))) {
                Bubble(
                    message = entry.message,
                    isMine = true,
                    dimmed = true,
                    modifier =
                        Modifier.combinedClickable(
                            onClick = onTap,
                            onClickLabel = stringResource(R.string.issue_comment_actions_cd),
                        ),
                )
                SendLabel(entry.state)
            }
        }
    }
}

@Composable
private fun CommentColumn(
    isMine: Boolean,
    header: @Composable () -> Unit,
    bubble: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = maxWidth * BUBBLE_MAX_WIDTH_FRACTION)
                    .align(if (isMine) Alignment.TopEnd else Alignment.TopStart),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
        ) {
            header()
            bubble()
        }
    }
}

/** The user's own bubbles are tinted and squared at the top right; everyone else's at the top left. */
@Composable
private fun Bubble(
    message: String,
    isMine: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val rounded = dimensionResource(R.dimen.issue_bubble_corner)
    val pointer = dimensionResource(R.dimen.issue_bubble_pointer_corner)
    val shape =
        RoundedCornerShape(
            topStart = if (isMine) rounded else pointer,
            topEnd = if (isMine) pointer else rounded,
            bottomEnd = rounded,
            bottomStart = rounded,
        )
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                .alpha(if (dimmed) DIMMED_ALPHA else 1f)
                .padding(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CommentHeader(
    author: String?,
    isAdmin: Boolean,
    dateLabel: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        Text(
            text = author ?: stringResource(R.string.requests_requester_unknown),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isAdmin) BingeTag(label = stringResource(R.string.issue_admin_tag))
        dateLabel?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SendIndicator(state: SendState) {
    val size = dimensionResource(R.dimen.issue_send_indicator_size)
    when (state) {
        SendState.Sending ->
            CircularProgressIndicator(
                strokeWidth = dimensionResource(DesR.dimen.progress_stroke_width),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size),
            )
        is SendState.Failed ->
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = stringResource(R.string.issue_comment_send_failed),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(size),
            )
    }
}

@Composable
private fun SendLabel(state: SendState) {
    val (textRes, color) =
        when (state) {
            SendState.Sending -> R.string.issue_comment_sending to MaterialTheme.colorScheme.onSurfaceVariant
            is SendState.Failed ->
                (if (state.retryable) R.string.issue_comment_send_failed else R.string.issue_comment_send_blocked) to
                    MaterialTheme.colorScheme.error
        }
    Text(stringResource(textRes), style = MaterialTheme.typography.bodySmall, color = color)
}
