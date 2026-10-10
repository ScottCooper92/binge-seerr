package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.TextEntrySurface
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ActionSheetGroup
import io.github.scottcooper92.binge.seerr.ui.state.actionItem
import io.github.scottcooper92.binge.seerr.ui.state.openItem

/** The composer: submitting hands the draft over and closes, since the pending row is the feedback. */
@Composable
internal fun CommentComposerSheet(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        TextEntrySurface(
            title = stringResource(R.string.issue_comment_title),
            value = draft,
            onValueChange = onDraftChange,
            onSubmit = onSubmit,
            onCancel = onDismiss,
            submitLabel = stringResource(R.string.issue_comment_send),
            hint = stringResource(R.string.issue_comment_hint),
            modifier = Modifier.imePadding(),
        )
    }
}

/** Editing a comment: saving is enabled only for a change; a failed save keeps the sheet and its text. */
@Composable
internal fun EditCommentSheet(
    title: String,
    draft: String,
    original: String,
    isSaving: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss, gesturesEnabled = !isSaving) {
        TextEntrySurface(
            title = title,
            value = draft,
            onValueChange = onDraftChange,
            onSubmit = onSubmit,
            onCancel = onDismiss,
            submitLabel = stringResource(R.string.issue_comment_save),
            submitEnabled = draft.isNotBlank() && draft.trim() != original.trim(),
            isSubmitting = isSaving,
            modifier = Modifier.imePadding(),
        )
    }
}

/** A comment the user may act on: edit, or delete through a confirm. */
@Composable
internal fun CommentActionsSheet(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        CommentActionsContent(
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

@Composable
internal fun CommentActionsContent(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionSheetGroup(
        rows =
            listOf(
                openItem(Icons.Filled.Edit, stringResource(R.string.issue_edit_comment), onClick = onEdit),
                actionItem(Icons.Filled.Delete, stringResource(R.string.issue_delete_comment), destructive = true, onClick = onDelete),
            ),
        modifier = modifier,
    )
}

/**
 * A pending comment: retried where a re-send may land, edited, or dropped. While its send is in flight ([sending]) Edit
 * and Discard are off, since a request already on the wire cannot be recalled; they come back once it lands or fails.
 */
@Composable
internal fun OutboxActionsSheet(
    retryable: Boolean,
    sending: Boolean,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDrop: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        OutboxActionsContent(
            retryable = retryable,
            sending = sending,
            onRetry = onRetry,
            onEdit = onEdit,
            onDrop = onDrop,
        )
    }
}

@Composable
internal fun OutboxActionsContent(
    retryable: Boolean,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
) {
    ActionSheetGroup(
        rows =
            listOfNotNull(
                actionItem(Icons.Filled.Refresh, stringResource(R.string.issue_comment_retry), onClick = onRetry).takeIf { retryable },
                openItem(Icons.Filled.Edit, stringResource(R.string.issue_edit_comment), enabled = !sending, onClick = onEdit),
                actionItem(
                    Icons.Filled.Delete,
                    stringResource(R.string.issue_comment_discard),
                    destructive = true,
                    enabled = !sending,
                    onClick = onDrop,
                ),
            ),
        modifier = modifier,
    )
}

@Composable
internal fun DeleteCommentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeConfirmDialog(
        title = stringResource(R.string.issue_delete_comment_title),
        message = stringResource(R.string.issue_delete_comment_message),
        confirmLabel = stringResource(R.string.issue_delete_comment),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
