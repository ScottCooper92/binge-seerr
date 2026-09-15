package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import com.binge.designsystem.R as DesR

/** What a sheet's destructive choice needs confirming before it runs. */
private enum class Pending { Decline, Remove }

/**
 * A request's actions: approve or retry above the line, the block toggle, then decline and remove
 * below it. Removing, and declining with a block, ask first; the primary label carries the toggle
 * so the tie is explicit.
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
) {
    var blockTitle by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<Pending?>(null) }
    BingeBottomSheet(onDismissRequest = onDismiss) {
        RequestActionsContent(
            item = item,
            actions = actions,
            blockTitle = blockTitle,
            onBlockTitleChange = { blockTitle = it },
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
        )
    }
    pending?.let { choice ->
        PendingConfirm(
            choice = choice,
            blockTitle = blockTitle,
            onConfirm = {
                pending = null
                if (choice == Pending.Remove) onRemove(blockTitle) else onDecline(blockTitle)
                onDismiss()
            },
            onCancel = { pending = null },
        )
    }
}

/**
 * The second step a destructive choice takes. Removing always asks; declining asks only when it also
 * blocks the title, which is the part that outlives the request.
 */
@Composable
private fun PendingConfirm(
    choice: Pending,
    blockTitle: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val removing = choice == Pending.Remove
    BingeConfirmDialog(
        title =
            stringResource(
                if (removing) R.string.request_remove_confirm_title else R.string.request_decline_block_confirm_title,
            ),
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
        onConfirm = onConfirm,
        onDismiss = onCancel,
    )
}

@Composable
internal fun RequestActionsContent(
    item: RequestItem,
    actions: RequestActions,
    blockTitle: Boolean,
    onBlockTitleChange: (Boolean) -> Unit,
    onApprove: () -> Unit,
    onRetry: () -> Unit,
    onDecline: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                ).padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Text(
            text = item.title ?: stringResource(item.mediaType.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_s)),
        )
        PositiveAction(actions, onApprove, onRetry)
        BlockTitleRow(actions, blockTitle, onBlockTitleChange)
        DestructiveActions(actions, blockTitle, onDecline, onRemove)
    }
}

/** Approve, or retry where the request already failed — one button, since the two never both apply. */
@Composable
private fun PositiveAction(
    actions: RequestActions,
    onApprove: () -> Unit,
    onRetry: () -> Unit,
) {
    if (!actions.canApprove && !actions.canRetry) return
    BingeFilledButton(
        label = stringResource(if (actions.canRetry) R.string.request_retry else R.string.request_approve),
        onClick = if (actions.canRetry) onRetry else onApprove,
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The block toggle, shown only where it has something to attach to: a decline or a remove below it. */
@Composable
private fun BlockTitleRow(
    actions: RequestActions,
    blockTitle: Boolean,
    onBlockTitleChange: (Boolean) -> Unit,
) {
    if (!actions.canBlock || !(actions.canDecline || actions.canRemove)) return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onBlockTitleChange(!blockTitle)
                }.padding(vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.request_block_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.request_block_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = blockTitle, onCheckedChange = onBlockTitleChange)
    }
}

/**
 * Decline and remove. The label carries the toggle rather than the toggle being a separate
 * confirmation, so what the button is about to do is on the button.
 *
 * Removing deletes the request and is always toned. A plain decline is a decision the request
 * survives, so it takes the error tone only once the toggle has added the block that outlives it —
 * which is the same thing the label and the confirmation already switch on.
 */
@Composable
private fun DestructiveActions(
    actions: RequestActions,
    blockTitle: Boolean,
    onDecline: () -> Unit,
    onRemove: () -> Unit,
) {
    val choices =
        buildList {
            if (actions.canDecline) {
                val label = if (blockTitle) R.string.request_decline_and_block else R.string.request_decline
                add(Triple(label, blockTitle, onDecline))
            }
            if (actions.canRemove) {
                val label = if (blockTitle) R.string.request_remove_and_block else R.string.request_remove
                add(Triple(label, true, onRemove))
            }
        }
    choices.forEach { (labelRes, destructive, onClick) ->
        BingeOutlinedButton(
            label = stringResource(labelRes),
            onClick = onClick,
            contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
