package com.cooper.binge.companion.seerr.ui.requests

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.formatRanges
import com.binge.designsystem.formatRelativeOrAbsolute
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.RequestStateChip
import com.cooper.binge.companion.seerr.ui.state.labelRes
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.binge.designsystem.R as DesR

/**
 * The request's context heads the sheet; the decision it is waiting on sits directly under that as
 * one bar; everything else is a list item in one of two groups — the **request**, then the
 * server's **media record** — each destructive row saying what it destroys.
 *
 * Its "requested 2 minutes ago" is worded against the moment the sheet opened. A sheet is up for seconds, so it does
 * not count down the way the page under it does (#1239).
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
        DetailLoadIndicator(model.detailLoad, callbacks.onRetryDetail)
        val requesterId =
            model.item.requestedById?.takeIf {
                callbacks.onOpenUser != null && canOpenUser(it, model.viewerId, model.canManageUsers)
            }
        RequestSheetHeader(model.item, showRequester = requesterId == null)
        DecisionBar(model.actions, blockTitle, model.progress, callbacks)
        val requestRows =
            listOfNotNull(requesterId?.let { id -> requesterRow(model.item) { callbacks.onOpenUser?.invoke(id) } }) +
                requestRows(model, blockTitle, onBlockTitleChange, callbacks)
        val mediaRows =
            model.media
                ?.takeIf { it.canManage }
                ?.let { mediaRows(it, model.progress, callbacks) }
                .orEmpty()
        if (requestRows.isNotEmpty()) ItemGroup(title = stringResource(R.string.request_sheet_group_request), rows = requestRows)
        if (mediaRows.isNotEmpty()) ItemGroup(title = stringResource(R.string.request_sheet_group_media), rows = mediaRows)
    }
}

/** A thin bar while the detail loads, and a one-line retry when it failed; nothing once it landed. */
@Composable
private fun DetailLoadIndicator(
    load: SheetDetailLoad,
    onRetry: () -> Unit,
) {
    when (load) {
        SheetDetailLoad.Loaded -> Unit
        SheetDetailLoad.Loading -> {
            val description = stringResource(R.string.request_sheet_loading)
            LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = description })
        }
        is SheetDetailLoad.Failed ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(load.error.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
            }
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
                    text = pluralStringResource(R.plurals.requests_seasons, item.seasonNumbers.size, item.seasonNumbers.formatRanges()),
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
): ListItem {
    val requester = item.requestedBy ?: stringResource(R.string.requests_requester_unknown)
    return ListItem(
        icon = Icons.Filled.Person,
        leadingContent = {
            BingeInitialsAvatar(
                name = requester,
                avatarUrl = item.requestedByAvatarUrl,
                size = dimensionResource(DesR.dimen.avatar_size_md),
            )
        },
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
    progress: ActionProgress?,
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
                        loading = progress?.action == RequestAction.Decline && !progress.failed,
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
): List<ListItem> {
    val actions = model.actions
    return buildList {
        if (model.canEdit) {
            add(ListItem(icon = Icons.Filled.Edit, label = stringResource(R.string.request_edit_title), onClick = callbacks.onEdit))
        }
        if (actions.canBlock && (actions.canDecline || actions.canRemove)) {
            add(
                ListItem(
                    icon = Icons.Filled.Block,
                    label = stringResource(R.string.request_block_title),
                    detail = stringResource(R.string.request_block_caption),
                    toggled = blockTitle,
                    onClick = { onBlockTitleChange(!blockTitle) },
                ),
            )
        }
        if (actions.canRemove) add(removeRow(model.progress, blockTitle, callbacks))
    }
}

/**
 * The Remove row through idle, running and failed. It keeps its label, height and place in all three, so nothing around
 * it moves. The trailing slot goes from nothing to the loading indicator to a Try again hint. Failed, the subtitle says
 * what happened in place of what Remove does, and a retry brings the usual one back.
 */
@Composable
private fun removeRow(
    progress: ActionProgress?,
    blockTitle: Boolean,
    callbacks: RequestSheetCallbacks,
): ListItem {
    val error = MaterialTheme.colorScheme.error
    val mine = progress?.takeIf { it.action == RequestAction.Remove }
    val running = mine != null && !mine.failed
    val failed = mine?.failed == true
    return ListItem(
        icon = Icons.Filled.Delete,
        iconTint = error,
        label = stringResource(if (blockTitle) R.string.request_remove_and_block else R.string.request_remove),
        detail = stringResource(removeDetailRes(blockTitle, failed)),
        detailColor = error.takeIf { failed },
        loading = running,
        clickable = !running,
        trailingContent =
            if (failed) {
                ({ Text(text = stringResource(R.string.action_try_again), style = MaterialTheme.typography.labelLarge, color = error) })
            } else {
                ({})
            },
        destination = ListItemDestination.Action,
        onClick = callbacks.onRemove,
    )
}

@StringRes
private fun removeDetailRes(
    blockTitle: Boolean,
    failed: Boolean,
): Int =
    when {
        failed -> R.string.request_remove_failed_detail
        blockTitle -> R.string.request_remove_block_detail
        else -> R.string.request_remove_detail
    }
