package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun IssueModals(
    state: IssueDetailUiState.Ready,
    events: Flow<IssueDetailEvent>,
    actions: IssueDetailActions,
    modals: IssueModalState,
) {
    val detail = state.detail
    // A save only actually lands on this event, never on the draft matching the server text: that's also
    // true the instant the sheet opens, before anything has been submitted.
    LaunchedEffect(events) {
        events.collectLatest { event ->
            if (event == IssueDetailEvent.CommentEdited) modals.editingCommentId = null
        }
    }
    if (modals.composing) {
        CommentComposerSheet(
            draft = state.draft,
            onDraftChange = actions.onDraftChange,
            onSubmit = {
                actions.onPostComment()
                modals.composing = false
            },
            onDismiss = { modals.composing = false },
        )
    }
    modals.actingOnCommentId?.let { commentId ->
        val comment = (listOfNotNull(detail.report) + detail.comments).firstOrNull { it.id == commentId }
        if (comment == null) {
            modals.actingOnCommentId = null
        } else {
            CommentActionsSheet(
                onEdit = { modals.openEdit(comment) },
                onDelete = { modals.deletingCommentId = commentId },
                onDismiss = { modals.actingOnCommentId = null },
            )
        }
    }
    modals.editingCommentId?.let { commentId ->
        val original = (listOfNotNull(detail.report) + detail.comments).firstOrNull { it.id == commentId }?.message.orEmpty()
        EditCommentSheet(
            title = stringResource(if (commentId == detail.report?.id) R.string.issue_edit_report else R.string.issue_edit_comment),
            draft = modals.editDraft,
            original = original,
            isSaving = state.commentAction == CommentAction.Editing(commentId),
            onDraftChange = { modals.editDraft = it },
            onSubmit = { actions.onEditComment(commentId, modals.editDraft) },
            onDismiss = { modals.editingCommentId = null },
        )
    }
    modals.deletingCommentId?.let { commentId ->
        DeleteCommentDialog(
            onConfirm = {
                modals.deletingCommentId = null
                actions.onDeleteComment(commentId)
            },
            onDismiss = { modals.deletingCommentId = null },
        )
    }
    OutboxModals(state, actions, modals)
}

@Composable
internal fun OutboxModals(
    state: IssueDetailUiState.Ready,
    actions: IssueDetailActions,
    modals: IssueModalState,
) {
    modals.outboxActionId?.let { localId ->
        state.outbox.firstOrNull { it.localId == localId }?.let { entry ->
            OutboxActionsSheet(
                retryable = (entry.state as? SendState.Failed)?.retryable == true,
                onRetry = { actions.onRetryOutbox(localId) },
                onEdit = { modals.openOutboxEdit(entry) },
                onDrop = { actions.onDropOutbox(localId) },
                onDismiss = { modals.outboxActionId = null },
            )
        }
    }
    modals.editingOutboxId?.let { localId ->
        EditCommentSheet(
            title = stringResource(R.string.issue_edit_comment),
            draft = modals.outboxEditDraft,
            original =
                state.outbox
                    .firstOrNull { it.localId == localId }
                    ?.message
                    .orEmpty(),
            isSaving = false,
            onDraftChange = { modals.outboxEditDraft = it },
            onSubmit = {
                actions.onEditOutbox(localId, modals.outboxEditDraft)
                modals.editingOutboxId = null
            },
            onDismiss = { modals.editingOutboxId = null },
        )
    }
}
