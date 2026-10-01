package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
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
    val viewerId: Int? = null,
    val canManageUsers: Boolean = false,
    /** The request's own detail is still loading, so the Request-by and Media rows have not arrived. */
    val isLoadingDetail: Boolean = false,
    /** That load failed, so those rows are missing and a retry is offered. */
    val detailError: SeerrError? = null,
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
    val onOpenUser: ((Int) -> Unit)? = null,
    val onRetryDetail: () -> Unit = {},
)

/**
 * The request's context heads the sheet; the decision it is waiting on sits directly under that as
 * one bar; everything else is a settings row in one of two groups — the **request**, then the
 * server's **media record** — each destructive row saying what it destroys.
 */
@Composable
internal fun RequestActionsContent(
    model: RequestSheetModel,
    callbacks: RequestSheetCallbacks,
    blockTitle: Boolean,
    onBlockTitleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (model.isLoadingDetail) LinearProgressIndicator(Modifier.fillMaxWidth())
        RequestActionsBody(model, callbacks, blockTitle, onBlockTitleChange)
    }
}

@Composable
private fun RequestActionsBody(
    model: RequestSheetModel,
    callbacks: RequestSheetCallbacks,
    blockTitle: Boolean,
    onBlockTitleChange: (Boolean) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        val requesterId =
            model.item.requestedById?.takeIf {
                callbacks.onOpenUser != null && canOpenUser(it, model.viewerId, model.canManageUsers)
            }
        RequestSheetHeader(model.item, showRequester = requesterId == null)
        model.detailError?.let { DetailLoadFailure(it, callbacks.onRetryDetail) }
        DecisionBar(model.actions, blockTitle, callbacks)
        val requestRows =
            listOfNotNull(requesterId?.let { id -> requesterRow(model.item) { callbacks.onOpenUser?.invoke(id) } }) +
                requestRows(model, blockTitle, onBlockTitleChange, callbacks)
        val mediaRows =
            model.media
                ?.takeIf { it.canManage }
                ?.let { mediaRows(it, callbacks) }
                .orEmpty()
        if (requestRows.isNotEmpty()) SettingsGroup(title = stringResource(R.string.request_sheet_group_request), rows = requestRows)
        if (mediaRows.isNotEmpty()) SettingsGroup(title = stringResource(R.string.request_sheet_group_media), rows = mediaRows)
    }
}

/** One line saying the rest of the sheet did not load, with a retry; the preview rows stay usable. */
@Composable
private fun DetailLoadFailure(
    error: SeerrError,
    onRetry: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        BingeTextButton(label = stringResource(R.string.action_try_again), onClick = onRetry)
    }
}

/** Poster, title, what it is, who asked and when, and where the request stands. */
@Composable
private fun RequestSheetHeader(
    item: RequestItem,
    showRequester: Boolean,
) {
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
            if (showRequester) {
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
            }
            val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.status?.let { RequestStateChip(status = it) }
                if (item.is4k) {
                    if (item.status != null) Spacer(Modifier.width(gap))
                    Text(
                        text = stringResource(R.string.settings_service_4k),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.seasonNumbers.isNotEmpty()) {
                Text(
                    text = pluralStringResource(R.plurals.requests_seasons, item.seasonNumbers.size, item.seasonNumbers.joinToString(", ")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Who asked, as a whole row that opens their profile; the request group's first row. */
@Composable
private fun requesterRow(
    item: RequestItem,
    onClick: () -> Unit,
): SettingsRow {
    val requester = item.requestedBy ?: stringResource(R.string.requests_requester_unknown)
    return SettingsRow(
        icon = Icons.Filled.Person,
        label = stringResource(R.string.request_sheet_requested_by, requester),
        detail = formatRelativeOrAbsolute(item.requestedAtMillis),
        onClick = onClick,
    )
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
        trailingContent = instance.status?.let { status -> { MediaStateChip(status = status) } },
        onClick = onMarkStatus,
    )

/**
 * Whether an instance in this state has files to delete. [MediaRecord.canDeleteFiles] is who may
 * delete at all; this is what there currently is to delete. `Processing` counts as a download
 * already in flight, and a null status (the server has nothing for this instance) does not.
 */
private fun SeerrMediaStatusCode?.hasFiles(): Boolean =
    this == SeerrMediaStatusCode.Processing || this == SeerrMediaStatusCode.PartiallyAvailable || this == SeerrMediaStatusCode.Available
