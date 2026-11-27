package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.companion.sdk.paging.ListRefresh
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.component.MediaResult
import com.binge.designsystem.component.MediaResultKind
import com.binge.designsystem.component.MediaResultRow
import com.binge.designsystem.downloadEtaLabel
import com.binge.designsystem.formatRanges
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchorIf
import com.binge.designsystem.template.PagedPhase
import com.cooper.binge.companion.seerr.feature.admin.R
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.PagedPullToRefresh
import com.cooper.binge.companion.seerr.ui.state.PullableMessage
import com.cooper.binge.companion.seerr.ui.state.RequestStateChip
import com.cooper.binge.companion.seerr.ui.state.belowPinnedLine
import com.cooper.binge.companion.seerr.ui.state.filteredPagePadding
import com.cooper.binge.companion.seerr.ui.state.rememberPagedPhase
import com.binge.designsystem.R as DesR
import com.cooper.binge.companion.seerr.core.ui.R as UiR

/**
 * The selected filter's rows with the states the pager reports, under a pull that refreshes them. Once rows are on
 * screen a refresh shows as the pull's spinner below the bar, or a tappable line when it failed; the full-screen
 * states are for a cache with nothing in it.
 *
 * @param pullState a still frame's resting pull; null remembers M3's own. See [PagedPullToRefresh].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsBody(
    filter: RequestFilter,
    lazyItems: LazyPagingItems<RequestItem>,
    /** This list's latest finished network refresh; see [rememberPagedPhase]. */
    lastRefresh: ListRefresh?,
    scope: ModerationScope,
    /** What the rows' relative times are worded against; see [RequestsUiState.Ready.now]. */
    now: Long,
    onOpen: (RequestItem) -> Unit,
    onManage: (RequestItem) -> Unit,
    /** A pull, beside the list's own refresh: what else on the page it refreshes. */
    onPull: () -> Unit,
    onOpenTitle: ((RequestItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    pullState: PullToRefreshState? = null,
) {
    val phase = lazyItems.rememberPagedPhase(lastRefresh)
    PagedPullToRefresh(
        phase = phase,
        loadState = lazyItems.loadState,
        onRefresh = {
            lazyItems.refresh()
            onPull()
        },
        contentPadding = contentPadding,
        modifier = modifier,
        state = pullState,
    ) {
        when (phase) {
            is PagedPhase.Rows ->
                if (phase.refreshError != null) {
                    // The failed line is pinned below the top bar and the header; the rows start below it while it shows.
                    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                        RefreshFailedLine(R.string.requests_refresh_failed, onRetry = lazyItems::retry)
                        RequestList(lazyItems, scope, now, onOpen, onManage, contentPadding.belowPinnedLine(), onOpenTitle)
                    }
                } else {
                    RequestList(lazyItems, scope, now, onOpen, onManage, contentPadding, onOpenTitle)
                }
            PagedPhase.Skeleton ->
                ListRowSkeletonColumn(
                    contentPadding = contentPadding.filteredPagePadding(),
                    modifier = Modifier.fillMaxSize(),
                    height = requestRowSkeletonHeight(),
                )
            is PagedPhase.Failed ->
                PagedRefreshError(
                    phase.error,
                    onRetry = lazyItems::retry,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            PagedPhase.Empty ->
                PullableMessage { fill ->
                    EmptyScreen(
                        message = stringResource(filter.emptyMessageRes()),
                        modifier = fill.padding(contentPadding),
                        icon = Icons.Filled.Inbox,
                    )
                }
        }
    }
}

/** What a request row is tall at the font in use: the skeleton's default height, or its text lines once they are taller. */
@Composable
private fun requestRowSkeletonHeight(): Dp =
    maxOf(
        dimensionResource(DesR.dimen.list_row_skeleton_height),
        dimensionResource(R.dimen.requests_row_skeleton_text) * LocalDensity.current.fontScale,
    )

@Composable
private fun RequestList(
    lazyItems: LazyPagingItems<RequestItem>,
    scope: ModerationScope,
    now: Long,
    onOpen: (RequestItem) -> Unit,
    onManage: (RequestItem) -> Unit,
    contentPadding: PaddingValues,
    onOpenTitle: ((RequestItem) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding.filteredPagePadding(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
    ) {
        items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
            lazyItems[index]?.let { item ->
                RequestRow(
                    item = item,
                    onClick = { onOpen(item) },
                    modifier = Modifier.layoutAnchorIf(index == 0, LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
                    onManage = { onManage(item) }.takeIf { item.reviewable(scope) },
                    now = now,
                    onOpenTitle = onOpenTitle?.let { open -> { open(item) } },
                )
            }
        }
        item {
            val append = lazyItems.loadState.mediator?.append ?: lazyItems.loadState.append
            PagedAppendState(append, onRetry = lazyItems::retry)
        }
    }
}

/**
 * One request on the shared media row: who asked and when above the title, the seasons after the title, then one line of status, kind,
 * 4K, year, rating and genre. A transferring download draws its bar under the row, and the poster opens the title where the caller allows.
 */
@Composable
fun RequestRow(
    item: RequestItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
    now: Long = System.currentTimeMillis(),
    onOpenTitle: (() -> Unit)? = null,
) {
    // Only a transferring grab earns a bar; a queued one stops at its chip.
    val download = item.download?.takeIf { it.downloading }
    val chip = item.statusChip()
    val title = item.title ?: stringResource(item.mediaType.labelRes())
    val subtitle =
        listOfNotNull(
            stringResource(item.mediaType.labelRes()),
            stringResource(UiR.string.settings_service_4k).takeIf { item.is4k },
            item.year,
        ).joinToString(stringResource(UiR.string.hub_meta_separator))
    val seasons =
        item.seasonNumbers
            .takeIf { it.isNotEmpty() }
            ?.let { pluralStringResource(UiR.plurals.requests_seasons, it.size, it.formatRanges()) }
    MediaResultRow(
        result =
            MediaResult(
                key = item.id.toString(),
                kind = MediaResultKind.Poster,
                imageUrl = item.posterUrl,
                name = title,
                subtitle = subtitle,
                rating = item.rating,
                meta = item.genre,
                nameSuffix = seasons,
                statusInline = true,
            ),
        onClick = onClick,
        modifier = modifier,
        trailing = { RequestStateChip(label = stringResource(chip.labelRes), tone = chip.tone) },
        byline = { RequestByline(item, now) },
        end =
            onManage?.let {
                {
                    IconButton(onClick = it) {
                        Icon(Icons.Filled.ThumbsUpDown, contentDescription = stringResource(R.string.request_primary_review))
                    }
                }
            },
        footer = download?.let { { DownloadFooter(it) } },
        onPosterClick = onOpenTitle,
        posterClickLabel = onOpenTitle?.let { stringResource(R.string.request_open_title_cd, title) },
    )
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
private fun RequestByline(
    item: RequestItem,
    now: Long,
) {
    val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
    Row(verticalAlignment = Alignment.CenterVertically) {
        item.requestedBy?.let { requester ->
            BingeInitialsAvatar(name = requester, avatarUrl = item.requestedByAvatarUrl)
            Spacer(Modifier.width(gap))
        }
        Text(
            text =
                listOfNotNull(
                    item.requestedBy ?: stringResource(UiR.string.requests_requester_unknown),
                    formatRelativeOrAbsolute(item.requestedAtMillis, now),
                ).joinToString(stringResource(UiR.string.hub_meta_separator)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
