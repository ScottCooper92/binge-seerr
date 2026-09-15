package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import com.binge.designsystem.R as DesR

/**
 * What the sheet renders: a request always, and the server's media record where there is one.
 *
 * The list's row sheet has neither a record nor an edit — a row is a request and nothing else — so
 * both are defaulted away rather than the list constructing an empty half.
 */
internal class RequestSheetModel(
    val item: RequestItem,
    val actions: RequestActions,
    val canEdit: Boolean = false,
    val media: MediaRecord? = null,
)

/** What its rows call back into, bundled so the content's parameter list stays readable. */
internal class RequestSheetCallbacks(
    val onApprove: () -> Unit,
    val onRetry: () -> Unit,
    val onDecline: () -> Unit,
    val onRemove: () -> Unit,
    val onEdit: () -> Unit = {},
    val onMarkStatus: (is4k: Boolean) -> Unit = {},
    val onDeleteFiles: (is4k: Boolean) -> Unit = {},
    val onClearData: () -> Unit = {},
)

/**
 * One sheet, two groups: what can be done to the **request**, and what can be done to the server's
 * **media record**. Two objects, which would normally argue against merging — but they are largely
 * mutually exclusive by state, so on any one title most of one group is absent.
 *
 * Whichever group the request's state makes relevant goes first: the request half while there is
 * still an approve or a retry to make, the media half once that is settled.
 */
@Composable
internal fun RequestActionsContent(
    model: RequestSheetModel,
    callbacks: RequestSheetCallbacks,
    blockTitle: Boolean,
    onBlockTitleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = model.item
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Text(
            text = item.title ?: stringResource(item.mediaType.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_s)),
        )
        val requestGroup: @Composable () -> Unit = {
            PositiveAction(model.actions, callbacks.onApprove, callbacks.onRetry)
            if (model.canEdit) {
                BingeOutlinedButton(
                    label = stringResource(R.string.request_edit_title),
                    onClick = callbacks.onEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BlockTitleRow(model.actions, blockTitle, onBlockTitleChange)
            DestructiveActions(model.actions, blockTitle, callbacks.onDecline, callbacks.onRemove)
        }
        val mediaGroup: @Composable () -> Unit = {
            model.media?.takeIf { it.canManage }?.let { record -> MediaGroup(record, callbacks) }
        }
        // An approve or a retry still to make is what "the request half is the live one" means.
        if (model.actions.canApprove || model.actions.canRetry) {
            requestGroup()
            mediaGroup()
        } else {
            mediaGroup()
            requestGroup()
        }
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
 * survives, so it takes the error tone only once the toggle has added the block that outlives it.
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

/** The server's record of the title, per instance. Clearing it sits last, since it takes the request too. */
@Composable
private fun MediaGroup(
    media: MediaRecord,
    callbacks: RequestSheetCallbacks,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(stringResource(R.string.media_manage), style = MaterialTheme.typography.titleSmall)
    media.instances.forEach { instance ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            Text(
                stringResource(if (instance.is4k) R.string.settings_service_4k else R.string.media_instance_standard),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            instance.status?.let { MediaStateChip(status = it) }
        }
        if (media.canSetStatus) {
            MarkAsRow { callbacks.onMarkStatus(instance.is4k) }
        }
        if (media.canDeleteFiles) {
            BingeOutlinedButton(
                label = stringResource(R.string.media_delete_files),
                onClick = { callbacks.onDeleteFiles(instance.is4k) },
                contentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (media.canClearData) {
        BingeOutlinedButton(
            label = stringResource(R.string.media_clear_data),
            onClick = callbacks.onClearData,
            contentColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Opens [MediaStatusSheet]. It carries no value of its own: the instance header above already reads
 * the current state out, and a second copy here is what the chip group used to do.
 */
@Composable
private fun MarkAsRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(onClick = onClick)
                .padding(vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            text = stringResource(R.string.media_mark_as),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
