package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
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
 * The request's context heads the sheet; the decision it is waiting on sits directly under that as
 * one bar; everything else is a settings row in one of two groups — the **request**, and the
 * server's **media record** — each destructive row saying what it destroys. The two groups are
 * largely exclusive by state, so whichever the request's state makes live goes first: the request
 * while there is still an approve or a retry to make, the media once that is settled.
 */
@Composable
internal fun RequestActionsContent(
    model: RequestSheetModel,
    callbacks: RequestSheetCallbacks,
    blockTitle: Boolean,
    onBlockTitleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        RequestSheetHeader(model.item)
        DecisionBar(model.actions, blockTitle, callbacks)
        val requestRows = requestRows(model, blockTitle, onBlockTitleChange, callbacks)
        val mediaRows =
            model.media
                ?.takeIf { it.canManage }
                ?.let { mediaRows(it, callbacks) }
                .orEmpty()
        val request: @Composable () -> Unit = {
            if (requestRows.isNotEmpty()) SettingsGroup(title = stringResource(R.string.request_sheet_group_request), rows = requestRows)
        }
        val media: @Composable () -> Unit = {
            if (mediaRows.isNotEmpty()) SettingsGroup(title = stringResource(R.string.request_sheet_group_media), rows = mediaRows)
        }
        if (model.actions.canApprove || model.actions.canRetry) {
            request()
            media()
        } else {
            media()
            request()
        }
    }
}

/** Poster, title, what it is, who asked and when, and where the request stands. */
@Composable
private fun RequestSheetHeader(item: RequestItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListRowPoster(imageUrl = item.posterUrl, contentDescription = null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
        ) {
            Text(
                text = item.title ?: stringResource(item.mediaType.labelRes()),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    listOfNotNull(
                        stringResource(item.mediaType.labelRes()),
                        item.year,
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val requester = item.requestedBy ?: stringResource(R.string.requests_requester_unknown)
            Text(
                text =
                    listOfNotNull(
                        stringResource(R.string.request_sheet_requested_by, requester),
                        formatRelativeOrAbsolute(item.requestedAtMillis),
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.status?.let { RequestStateChip(status = it) }
        }
    }
}

/**
 * The one decision the request is waiting on, as a pair side by side: decline beside approve. A
 * failed request has only retry. The decline label carries the block switch below, as it does today.
 */
@Composable
private fun DecisionBar(
    actions: RequestActions,
    blockTitle: Boolean,
    callbacks: RequestSheetCallbacks,
) {
    when {
        actions.canRetry ->
            BingeFilledButton(
                label = stringResource(R.string.request_retry),
                onClick = callbacks.onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        actions.canApprove ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                if (actions.canDecline) {
                    BingeOutlinedButton(
                        label = stringResource(if (blockTitle) R.string.request_decline_and_block else R.string.request_decline),
                        onClick = callbacks.onDecline,
                        destructive = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                BingeFilledButton(
                    label = stringResource(R.string.request_approve),
                    onClick = callbacks.onApprove,
                    modifier = Modifier.weight(1f),
                )
            }
    }
}

@Composable
private fun requestRows(
    model: RequestSheetModel,
    blockTitle: Boolean,
    onBlockTitleChange: (Boolean) -> Unit,
    callbacks: RequestSheetCallbacks,
): List<SettingsRow> {
    val actions = model.actions
    val error = MaterialTheme.colorScheme.error
    return buildList {
        if (model.canEdit) {
            add(SettingsRow(icon = Icons.Filled.Edit, label = stringResource(R.string.request_edit_title), onClick = callbacks.onEdit))
        }
        if (actions.canBlock && (actions.canDecline || actions.canRemove)) {
            add(
                SettingsRow(
                    icon = Icons.Filled.Block,
                    label = stringResource(R.string.request_block_title),
                    detail = stringResource(R.string.request_block_caption),
                    trailingContent = { Switch(checked = blockTitle, onCheckedChange = onBlockTitleChange) },
                    onClick = { onBlockTitleChange(!blockTitle) },
                ),
            )
        }
        if (actions.canRemove) {
            add(
                SettingsRow(
                    icon = Icons.Filled.Delete,
                    iconTint = error,
                    label = stringResource(if (blockTitle) R.string.request_remove_and_block else R.string.request_remove),
                    detail = stringResource(if (blockTitle) R.string.request_remove_block_detail else R.string.request_remove_detail),
                    trailingContent = {},
                    onClick = callbacks.onRemove,
                ),
            )
        }
    }
}

/**
 * Each instance is one row carrying its state; where the status can be set the row opens the picker,
 * so "what it is" and "change it" are one thing rather than a label and a separate Mark as row.
 */
@Composable
private fun mediaRows(
    media: MediaRecord,
    callbacks: RequestSheetCallbacks,
): List<SettingsRow> {
    val error = MaterialTheme.colorScheme.error
    val client = stringResource(if (media.isTv) R.string.media_client_sonarr else R.string.media_client_radarr)
    return buildList {
        media.instances.forEach { instance ->
            add(instanceRow(instance, media.canSetStatus) { callbacks.onMarkStatus(instance.is4k) })
            if (media.canDeleteFiles && instance.status.hasFiles()) {
                add(
                    SettingsRow(
                        icon = Icons.Filled.DeleteSweep,
                        iconTint = error,
                        label = stringResource(if (instance.is4k) R.string.media_delete_4k_files else R.string.media_delete_files),
                        detail = stringResource(R.string.media_delete_files_detail, client),
                        trailingContent = {},
                        onClick = { callbacks.onDeleteFiles(instance.is4k) },
                    ),
                )
            }
        }
        if (media.canClearData) {
            add(
                SettingsRow(
                    icon = Icons.Filled.DeleteForever,
                    iconTint = error,
                    label = stringResource(R.string.media_clear_data),
                    detail = stringResource(R.string.media_clear_detail),
                    trailingContent = {},
                    onClick = callbacks.onClearData,
                ),
            )
        }
    }
}

@Composable
private fun instanceRow(
    instance: MediaInstance,
    canSetStatus: Boolean,
    onMarkStatus: () -> Unit,
): SettingsRow =
    SettingsRow(
        icon = Icons.Filled.Movie,
        label = stringResource(if (instance.is4k) R.string.settings_service_4k else R.string.media_instance_standard),
        detail = stringResource(R.string.media_mark_as).takeIf { canSetStatus },
        clickable = canSetStatus,
        trailingContent = { instance.status?.let { MediaStateChip(status = it) } },
        onClick = onMarkStatus,
    )

/**
 * Whether an instance in this state has files to delete. [MediaRecord.canDeleteFiles] is who may
 * delete at all; this is what there currently is to delete. `Processing` counts as a download
 * already in flight, and a null status (the server has nothing for this instance) does not.
 */
private fun SeerrMediaStatusCode?.hasFiles(): Boolean =
    this == SeerrMediaStatusCode.Processing || this == SeerrMediaStatusCode.PartiallyAvailable || this == SeerrMediaStatusCode.Available
