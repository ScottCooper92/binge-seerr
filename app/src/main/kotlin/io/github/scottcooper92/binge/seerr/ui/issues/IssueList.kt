package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowHeader
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.MediaTypeTag
import com.binge.designsystem.component.MediaTypeTagType
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.PagedRefreshError
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import com.binge.designsystem.R as DesR

/**
 * The selected filter's rows with the states the pager reports. The rows come from the cache, so
 * the server's refresh shows over them as a thin bar, or as a tappable line when it failed; the
 * full-screen states are for a cache with nothing in it.
 */
@Composable
internal fun IssuesBody(
    filter: IssueFilter,
    lazyItems: LazyPagingItems<IssueItem>,
    onOpen: (IssueItem) -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remote = lazyItems.loadState.mediator?.refresh ?: lazyItems.loadState.refresh
    when {
        lazyItems.itemCount > 0 ->
            Column(modifier.fillMaxSize()) {
                if (remote is LoadState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (remote is LoadState.Error) RefreshFailedLine(remote.error, onRetry = lazyItems::retry, onReconnect = onReconnect)
                IssueList(lazyItems, onOpen, onReconnect)
            }
        remote is LoadState.Loading || lazyItems.loadState.refresh is LoadState.Loading -> LoadingScreen(modifier)
        remote is LoadState.Error ->
            PagedRefreshError(
                remote.error,
                onRetry = lazyItems::retry,
                onReconnect = onReconnect,
                modifier = modifier,
            )
        else -> EmptyScreen(message = stringResource(filter.emptyMessageRes()), modifier = modifier, icon = Icons.Filled.ReportProblem)
    }
}

@Composable
private fun RefreshFailedLine(
    error: Throwable,
    onRetry: () -> Unit,
    onReconnect: () -> Unit,
) {
    val rejected = error.toSeerrError() == SeerrError.Unauthorized
    Text(
        text = stringResource(if (rejected) R.string.requests_reconnect else R.string.issues_refresh_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = if (rejected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { if (rejected) onReconnect() else onRetry() }
                .padding(
                    horizontal = dimensionResource(DesR.dimen.screen_content_inset),
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
    )
}

@Composable
private fun IssueList(
    lazyItems: LazyPagingItems<IssueItem>,
    onOpen: (IssueItem) -> Unit,
    onReconnect: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
    ) {
        items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
            lazyItems[index]?.let { item -> IssueRow(item = item, onClick = { onOpen(item) }) }
        }
        item {
            val append = lazyItems.loadState.mediator?.append ?: lazyItems.loadState.append
            PagedAppendState(append, onRetry = lazyItems::retry, onReconnect = onReconnect)
        }
    }
}

/** One issue: poster, title, the state chip, its type and scope, the opening line, and who filed it when. */
@Composable
internal fun IssueRow(
    item: IssueItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    ListRow(
        modifier = modifier,
        onClick = onClick,
        leading = { ListRowPoster(imageUrl = item.posterUrl, contentDescription = null) },
    ) { contentModifier -> IssueRowMeta(item, now, contentModifier) }
}

@Composable
private fun IssueRowMeta(
    item: IssueItem,
    now: Long,
    modifier: Modifier,
) {
    val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
    Column(modifier = modifier.padding(top = dimensionResource(DesR.dimen.detail_meta_spacing))) {
        ListRowHeader(
            title = item.title ?: stringResource(item.mediaType.labelRes()),
            trailing = { RequestStateChip(label = stringResource(item.status.labelRes()), tone = item.status.tone()) },
        )
        Spacer(Modifier.height(gap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaTypeTag(type = if (item.mediaType == RequestMediaType.Movie) MediaTypeTagType.Movie else MediaTypeTagType.Tv)
            Spacer(Modifier.width(gap))
            Text(
                listOfNotNull(
                    stringResource(item.type.labelRes()),
                    issueAffectedLabel(item),
                ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.problem?.let { problem ->
            Spacer(Modifier.height(gap))
            Text(
                text = problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(gap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.reportedBy?.let { reporter ->
                BingeInitialsAvatar(name = reporter)
                Spacer(Modifier.width(gap))
            }
            Text(
                text =
                    listOfNotNull(
                        item.reportedBy ?: stringResource(R.string.requests_requester_unknown),
                        item.commentCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.issue_comments, it, it) },
                        formatRelativeOrAbsolute(item.createdAtMillis, now),
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The part of a show an issue affects; a movie is always the whole film, so it carries none. */
@Composable
internal fun issueAffectedLabel(item: IssueItem): String? {
    val season = item.problemSeason?.takeIf { it > 0 && item.mediaType == RequestMediaType.Tv } ?: return null
    val episode = item.problemEpisode?.takeIf { it > 0 }
    return if (episode != null) {
        stringResource(R.string.issue_affected_episode, season, episode)
    } else {
        stringResource(R.string.request_season_number, season)
    }
}
