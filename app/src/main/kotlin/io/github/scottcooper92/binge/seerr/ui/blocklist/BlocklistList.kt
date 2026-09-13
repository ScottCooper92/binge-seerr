package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowHeader
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.MediaTypeTag
import com.binge.designsystem.component.MediaTypeTagType
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.PagedRefreshError
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/** The rows with the states the pager reports; a filter or search that matches nothing reads differently from an empty list. */
@Composable
internal fun BlocklistBody(
    lazyItems: LazyPagingItems<BlocklistItem>,
    isFiltered: Boolean,
    actingTmdbIds: Set<Int>,
    canManage: Boolean,
    onOpen: (BlocklistItem) -> Unit,
    onRemove: (BlocklistItem) -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = lazyItems.loadState.refresh
    when {
        lazyItems.itemCount > 0 ->
            Column(modifier.fillMaxSize()) {
                if (refresh is LoadState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                BlocklistList(lazyItems, actingTmdbIds, canManage, onOpen, onRemove, onReconnect)
            }
        refresh is LoadState.Loading -> LoadingScreen(modifier)
        refresh is LoadState.Error ->
            PagedRefreshError(
                refresh.error,
                onRetry = lazyItems::retry,
                onReconnect = onReconnect,
                modifier = modifier,
            )
        else ->
            EmptyScreen(
                message = stringResource(if (isFiltered) R.string.blocklist_empty_filtered else R.string.blocklist_empty),
                modifier = modifier,
                icon = Icons.Filled.Block,
            )
    }
}

@Composable
private fun BlocklistList(
    lazyItems: LazyPagingItems<BlocklistItem>,
    actingTmdbIds: Set<Int>,
    canManage: Boolean,
    onOpen: (BlocklistItem) -> Unit,
    onRemove: (BlocklistItem) -> Unit,
    onReconnect: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
    ) {
        items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
            lazyItems[index]?.let { item ->
                BlocklistRow(
                    item = item,
                    isActing = item.tmdbId in actingTmdbIds,
                    onClick = { onOpen(item) },
                    onRemove = if (canManage) ({ onRemove(item) }) else null,
                )
            }
        }
        item { PagedAppendState(lazyItems.loadState.append, onRetry = lazyItems::retry, onReconnect = onReconnect) }
    }
}

/** One blocked title: poster, title and type, who blocked it and when, and the tags it fell to. */
@Composable
internal fun BlocklistRow(
    item: BlocklistItem,
    isActing: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ListRow(
        modifier = modifier,
        onClick = onClick,
        enabled = !isActing,
        leading = { ListRowPoster(imageUrl = item.posterUrl, contentDescription = null, dimmed = true) },
        trailing =
            onRemove?.let { remove ->
                {
                    BingeTextButton(
                        label = stringResource(R.string.blocklist_unblock),
                        onClick = remove,
                        enabled = !isActing,
                        loading = isActing,
                    )
                }
            },
    ) { contentModifier -> BlocklistRowMeta(item, contentModifier) }
}

@Composable
private fun BlocklistRowMeta(
    item: BlocklistItem,
    modifier: Modifier,
) {
    val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
    Column(modifier = modifier.padding(top = dimensionResource(DesR.dimen.detail_meta_spacing))) {
        ListRowHeader(title = item.title ?: stringResource(item.mediaType.labelRes()))
        Spacer(Modifier.height(gap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaTypeTag(type = if (item.mediaType == RequestMediaType.Movie) MediaTypeTagType.Movie else MediaTypeTagType.Tv)
            item.year?.let { year ->
                Spacer(Modifier.width(gap))
                Text(year, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        val at = formatRelativeOrAbsolute(item.addedAtMillis)
        val by = item.addedBy?.let { stringResource(R.string.blocklist_blocked_by, it) }
        listOfNotNull(by, at).takeIf { it.isNotEmpty() }?.let { parts ->
            Spacer(Modifier.height(gap))
            Text(
                parts.joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.tags.isNotEmpty()) {
            Spacer(Modifier.height(gap))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
            ) {
                item.tags.forEach { tag -> BingeTag(label = tag) }
            }
        }
    }
}
