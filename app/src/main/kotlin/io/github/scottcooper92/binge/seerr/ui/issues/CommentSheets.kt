package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.TextEntrySurface
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

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
            onEdit = {
                onDismiss()
                onEdit()
            },
            onDelete = {
                onDismiss()
                onDelete()
            },
        )
    }
}

@Composable
internal fun CommentActionsContent(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
        ActionRow(Icons.Filled.Edit, stringResource(R.string.issue_edit_comment), onEdit)
        ActionRow(Icons.Filled.Delete, stringResource(R.string.issue_delete_comment), onDelete, tint = MaterialTheme.colorScheme.error)
    }
}

/** A pending comment: retried where a re-send may land, edited, or dropped; it never reached the server. */
@Composable
internal fun OutboxActionsSheet(
    retryable: Boolean,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDrop: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        OutboxActionsContent(
            retryable = retryable,
            onRetry = {
                onDismiss()
                onRetry()
            },
            onEdit = {
                onDismiss()
                onEdit()
            },
            onDrop = {
                onDismiss()
                onDrop()
            },
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
) {
    Column(modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
        if (retryable) ActionRow(Icons.Filled.Refresh, stringResource(R.string.issue_comment_retry), onRetry)
        ActionRow(Icons.Filled.Edit, stringResource(R.string.issue_edit_comment), onEdit)
        ActionRow(Icons.Filled.Delete, stringResource(R.string.issue_comment_discard), onDrop, tint = MaterialTheme.colorScheme.error)
    }
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

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(onClick = onClick)
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
