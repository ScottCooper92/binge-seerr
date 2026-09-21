package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowHeader
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.component.MediaTypeTag
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.belowPinnedLine
import io.github.scottcooper92.binge.seerr.ui.state.downloadEtaLabel
import com.binge.designsystem.R as DesR

/**
 * The selected filter's rows with the states the pager reports. Once rows are on screen a refresh
 * shows as a thin bar over them; the full-screen loader is for the first load, when there is
 * nothing to keep.
 */
@Composable
internal fun RequestsBody(
    filter: RequestFilter,
    lazyItems: LazyPagingItems<RequestItem>,
    scope: ModerationScope,
    actingIds: Set<Int>,
    onOpen: (RequestItem) -> Unit,
    onRemove: (RequestItem) -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val refreshState = lazyItems.loadState.refresh
    when {
        lazyItems.itemCount > 0 && refreshState is LoadState.Loading ->
            // A refresh line is pinned below the top bar and the header; the rows start below it while it shows.
            Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                RequestList(lazyItems, scope, actingIds, onOpen, onRemove, onReconnect, contentPadding.belowPinnedLine())
            }
        lazyItems.itemCount > 0 -> RequestList(lazyItems, scope, actingIds, onOpen, onRemove, onReconnect, contentPadding)
        refreshState is LoadState.Loading ->
            ListRowSkeletonColumn(
                contentPadding = PaddingValues(resolvedContentInset()) + contentPadding,
                modifier = modifier,
            )
        refreshState is LoadState.Error ->
            PagedRefreshError(
                refreshState.error,
                onRetry = lazyItems::retry,
                onReconnect = onReconnect,
                modifier = modifier.padding(contentPadding),
            )
        else ->
            EmptyScreen(
                message = stringResource(filter.emptyMessageRes()),
                modifier = modifier.padding(contentPadding),
                icon = Icons.Filled.Inbox,
            )
    }
}

@Composable
private fun RequestList(
    lazyItems: LazyPagingItems<RequestItem>,
    scope: ModerationScope,
    actingIds: Set<Int>,
    onOpen: (RequestItem) -> Unit,
    onRemove: (RequestItem) -> Unit,
    onReconnect: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(resolvedContentInset()) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
    ) {
        items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
            lazyItems[index]?.let { item ->
                RequestRow(
                    item = item,
                    onClick = { onOpen(item) },
                    onRemove = { onRemove(item) }.takeIf { item.actions(scope).canRemove },
                    isActing = item.id in actingIds,
                )
            }
        }
        item { PagedAppendState(lazyItems.loadState.append, onRetry = lazyItems::retry, onReconnect = onReconnect) }
    }
}

@Composable
internal fun RequestRow(
    item: RequestItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    isActing: Boolean = false,
    now: Long = System.currentTimeMillis(),
) {
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    // Only a transferring grab earns a bar; a queued one stops at its chip.
    val download = item.download?.takeIf { it.downloading }
    ListRow(
        modifier = modifier,
        onClick = onClick,
        enabled = !isActing,
        verticalAlignment = Alignment.CenterVertically,
        leading = { ListRowPoster(imageUrl = item.posterUrl, contentDescription = null, dimmed = isActing) },
        trailing =
            onRemove?.let {
                {
                    IconButton(onClick = { showRemoveConfirm = true }, enabled = !isActing) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.request_remove))
                    }
                }
            },
        footer = download?.let { { DownloadFooter(it) } },
    ) { contentModifier -> RequestRowMeta(item, now, contentModifier) }
    if (showRemoveConfirm) {
        BingeConfirmDialog(
            title = stringResource(R.string.request_remove_confirm_title),
            message = stringResource(R.string.request_remove_confirm_message),
            confirmLabel = stringResource(R.string.request_remove),
            destructive = true,
            onConfirm = {
                showRemoveConfirm = false
                onRemove?.invoke()
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }
}

@Composable
private fun DownloadFooter(download: RequestDownload) {
    Column {
        LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth())
        download.etaMinutes?.let { eta ->
            Spacer(Modifier.height(dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)))
            Text(downloadEtaLabel(eta), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RequestRowMeta(
    item: RequestItem,
    now: Long,
    modifier: Modifier,
) {
    val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
    // modifier already carries fillMaxHeight() from ListRow's content slot, so this column matches
    // the poster's height and the trailing spacer below can push the requester line to its bottom.
    Column(modifier = modifier.padding(top = dimensionResource(DesR.dimen.detail_meta_spacing))) {
        val chip = item.statusChip()
        ListRowHeader(title = item.title ?: stringResource(item.mediaType.labelRes()))
        Spacer(Modifier.height(gap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RequestStateChip(label = stringResource(chip.labelRes), tone = chip.tone)
            Spacer(Modifier.width(gap))
            MediaTypeTag(type = item.mediaType.toTagType())
            if (item.is4k) {
                Spacer(Modifier.width(gap))
                Text(
                    stringResource(R.string.settings_service_4k),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.year?.let { year ->
                Spacer(Modifier.width(gap))
                Text(
                    year,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (item.seasonNumbers.isNotEmpty()) {
            Spacer(Modifier.height(gap))
            Text(
                text = pluralStringResource(R.plurals.requests_seasons, item.seasonNumbers.size, item.seasonNumbers.joinToString(", ")),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f).heightIn(min = gap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.requestedBy?.let { requester ->
                BingeInitialsAvatar(name = requester)
                Spacer(Modifier.width(gap))
            }
            Text(
                text =
                    listOfNotNull(
                        item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                        formatRelativeOrAbsolute(item.requestedAtMillis, now),
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
