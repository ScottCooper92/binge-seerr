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
        BingeConfirmDialog(
            title =
                stringResource(
                    if (choice ==
                        Pending.Remove
                    ) {
                        R.string.request_remove_confirm_title
                    } else {
                        R.string.request_decline_block_confirm_title
                    },
                ),
            message =
                stringResource(
                    when {
                        choice == Pending.Remove && blockTitle -> R.string.request_remove_block_confirm_message
                        choice == Pending.Remove -> R.string.request_remove_confirm_message
                        else -> R.string.request_decline_block_confirm_message
                    },
                ),
            confirmLabel = stringResource(if (choice == Pending.Remove) R.string.request_remove else R.string.request_decline),
            destructive = true,
            onConfirm = {
                pending = null
                if (choice == Pending.Remove) onRemove(blockTitle) else onDecline(blockTitle)
                onDismiss()
            },
            onDismiss = { pending = null },
        )
    }
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
        if (actions.canApprove || actions.canRetry) {
            BingeFilledButton(
                label = stringResource(if (actions.canRetry) R.string.request_retry else R.string.request_approve),
                onClick = if (actions.canRetry) onRetry else onApprove,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (actions.canBlock && (actions.canDecline || actions.canRemove)) {
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
        if (actions.canDecline) {
            BingeOutlinedButton(
                label = stringResource(if (blockTitle) R.string.request_decline_and_block else R.string.request_decline),
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (actions.canRemove) {
            BingeOutlinedButton(
                label = stringResource(if (blockTitle) R.string.request_remove_and_block else R.string.request_remove),
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
