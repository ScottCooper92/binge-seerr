package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError

/**
 * Everything that changes something, in one sheet, behind the page's one button.
 *
 * Every irreversible choice here is a [Pending] rather than each having its own flag: the sheet had
 * two confirmation channels when it was two sheets, and one channel is what keeps a new destructive
 * action from arriving with a third.
 */
private sealed interface Pending : java.io.Serializable {
    data object Decline : Pending

    data object Remove : Pending

    data object ClearData : Pending

    /** Deleting files asks twice, the second time naming the client the files leave. */
    data class DeleteFiles(
        val is4k: Boolean,
        val second: Boolean = false,
    ) : Pending
}

/**
 * A request's actions, and — where the caller has one — the server's media record beside them.
 *
 * The list's row sheet passes neither [media] nor [canEdit]: a row is a request and nothing else.
 * The detail page passes both, so the two objects it can act on live behind its one button.
 */
@Composable
internal fun RequestActionsSheet(
    item: RequestItem,
    actions: RequestActions,
    onApprove: () -> Unit,
    onRetry: () -> Unit,
    onDecline: (blockTitle: Boolean) -> Unit,
    onRemove: (blockTitle: Boolean) -> Unit,
    onDismiss: () -> Unit,
    canEdit: Boolean = false,
    onEdit: () -> Unit = {},
    media: MediaRecord? = null,
    mediaActions: ManageMediaActions? = null,
    onMarkStatus: (is4k: Boolean) -> Unit = {},
) {
    var blockTitle by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<Pending?>(null) }
    BingeBottomSheet(onDismissRequest = onDismiss) {
        RequestActionsContent(
            model = RequestSheetModel(item = item, actions = actions, canEdit = canEdit, media = media),
            callbacks =
                RequestSheetCallbacks(
                    onApprove = {
                        onApprove()
                        onDismiss()
                    },
                    onRetry = {
                        onRetry()
                        onDismiss()
                    },
                    onDecline = {
                        if (blockTitle) {
                            pending = Pending.Decline
                        } else {
                            onDecline(false)
                            onDismiss()
                        }
                    },
                    onRemove = { pending = Pending.Remove },
                    onEdit = {
                        onDismiss()
                        onEdit()
                    },
                    // Sequential, never stacked: each sheet owns a window and a scrim.
                    onMarkStatus = { is4k ->
                        onDismiss()
                        onMarkStatus(is4k)
                    },
                    onDeleteFiles = { is4k -> pending = Pending.DeleteFiles(is4k) },
                    onClearData = { pending = Pending.ClearData },
                ),
            blockTitle = blockTitle,
            onBlockTitleChange = { blockTitle = it },
        )
    }
    pending?.let { step ->
        PendingConfirm(
            step = step,
            media = media,
            mediaActions = mediaActions,
            blockTitle = blockTitle,
            onStep = { pending = it },
            onDone = {
                pending = null
                onDismiss()
            },
            onDecline = onDecline,
            onRemove = onRemove,
        )
    }
}

/**
 * The second step every destructive choice takes. Declining asks only when it also blocks the
 * title, which is the part that outlives the request; removing and both media deletions always ask.
 */
@Composable
private fun PendingConfirm(
    step: Pending,
    media: MediaRecord?,
    mediaActions: ManageMediaActions?,
    blockTitle: Boolean,
    onStep: (Pending?) -> Unit,
    onDone: () -> Unit,
    onDecline: (Boolean) -> Unit,
    onRemove: (Boolean) -> Unit,
) {
    when (step) {
        Pending.Decline, Pending.Remove ->
            RequestConfirm(
                removing = step == Pending.Remove,
                blockTitle = blockTitle,
                onConfirm = { if (step == Pending.Remove) onRemove(blockTitle) else onDecline(blockTitle) },
                onStep = onStep,
                onDone = onDone,
            )
        Pending.ClearData ->
            BingeConfirmDialog(
                title = stringResource(R.string.media_clear_confirm_title),
                message = stringResource(R.string.media_clear_confirm_message),
                confirmLabel = stringResource(R.string.media_clear_data),
                destructive = true,
                onConfirm = {
                    media?.mediaId?.let { mediaActions?.onClearData?.invoke(it) }
                    onDone()
                },
                onDismiss = { onStep(null) },
            )
        is Pending.DeleteFiles -> DeleteFilesConfirm(step, media, mediaActions, onStep, onDone)
    }
}

@Composable
private fun RequestConfirm(
    removing: Boolean,
    blockTitle: Boolean,
    onConfirm: () -> Unit,
    onStep: (Pending?) -> Unit,
    onDone: () -> Unit,
) {
    BingeConfirmDialog(
        title = stringResource(if (removing) R.string.request_remove_confirm_title else R.string.request_decline_block_confirm_title),
        message =
            stringResource(
                when {
                    removing && blockTitle -> R.string.request_remove_block_confirm_message
                    removing -> R.string.request_remove_confirm_message
                    else -> R.string.request_decline_block_confirm_message
                },
            ),
        confirmLabel = stringResource(if (removing) R.string.request_remove else R.string.request_decline),
        destructive = true,
        onConfirm = {
            onConfirm()
            onDone()
        },
        onDismiss = { onStep(null) },
    )
}

/** Two steps, the second naming the client the files leave — deleting them is not undone by a re-request. */
@Composable
private fun DeleteFilesConfirm(
    step: Pending.DeleteFiles,
    media: MediaRecord?,
    mediaActions: ManageMediaActions?,
    onStep: (Pending?) -> Unit,
    onDone: () -> Unit,
) {
    val client = stringResource(if (media?.isTv == true) R.string.media_client_sonarr else R.string.media_client_radarr)
    BingeConfirmDialog(
        title =
            if (step.second) {
                stringResource(R.string.media_delete_files_second_title, client)
            } else {
                stringResource(R.string.media_delete_files_confirm_title)
            },
        message =
            if (step.second) {
                stringResource(R.string.media_delete_files_second_message, client)
            } else {
                stringResource(R.string.media_delete_files_confirm_message)
            },
        confirmLabel = stringResource(R.string.media_delete_files),
        destructive = true,
        onConfirm = {
            if (step.second) {
                media?.mediaId?.let { mediaActions?.onDeleteFiles?.invoke(it, step.is4k) }
                onDone()
            } else {
                onStep(step.copy(second = true))
            }
        },
        onDismiss = { onStep(null) },
    )
}

/** The snackbar line for a moderation's outcome. */
@StringRes
internal fun ModerationEvent.messageRes(): Int =
    (
        when (this) {
            ModerationEvent.Approved -> R.string.request_approved
            ModerationEvent.Retried -> R.string.request_retried
            ModerationEvent.Edited -> R.string.request_edited
            ModerationEvent.MediaStatusSet -> R.string.media_status_set
            ModerationEvent.MediaCleared -> R.string.media_cleared
            ModerationEvent.MediaFilesDeleted -> R.string.media_files_deleted
            ModerationEvent.Declined -> R.string.request_declined
            ModerationEvent.DeclinedAndBlocked -> R.string.request_declined_and_blocked
            ModerationEvent.DeclinedButBlockFailed -> R.string.request_declined_block_failed
            ModerationEvent.Removed -> R.string.request_removed
            ModerationEvent.RemovedAndBlocked -> R.string.request_removed_and_blocked
            ModerationEvent.RemovedButBlockFailed -> R.string.request_removed_block_failed
            is ModerationEvent.Failed ->
                if (error ==
                    SeerrError.Unauthorized
                ) {
                    R.string.hub_unauthorized_body
                } else {
                    R.string.request_action_failed
                }
        }
    )

internal fun ModerationEvent.isError(): Boolean =
    this is ModerationEvent.Failed || this == ModerationEvent.DeclinedButBlockFailed || this == ModerationEvent.RemovedButBlockFailed
