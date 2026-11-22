package com.cooper.binge.companion.seerr.ui.issues

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.companion.sdk.paging.ListRefresh
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.FontScaleHeight
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.component.MediaResult
import com.binge.designsystem.component.MediaResultKind
import com.binge.designsystem.component.MediaResultRow
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchorIf
import com.binge.designsystem.template.PagedPhase
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.PagedAppendState
import com.cooper.binge.companion.seerr.ui.requests.PagedRefreshError
import com.cooper.binge.companion.seerr.ui.requests.RefreshFailedLine
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.requests.labelRes
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.PagedPullToRefresh
import com.cooper.binge.companion.seerr.ui.state.PullableMessage
import com.cooper.binge.companion.seerr.ui.state.RequestStateChip
import com.cooper.binge.companion.seerr.ui.state.belowPinnedLine
import com.cooper.binge.companion.seerr.ui.state.filteredPagePadding
import com.cooper.binge.companion.seerr.ui.state.rememberPagedPhase
import com.binge.designsystem.R as DesR

/**
 * The selected filter's rows with the states the pager reports, under a pull that refreshes them. The rows come
 * from the cache, so the server's refresh shows over them as the pull's spinner below the bar, or as a tappable line
 * when it failed; the full-screen states are for a cache with nothing in it.
 *
 * @param pullState a still frame's resting pull; null remembers M3's own. See [PagedPullToRefresh].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IssuesBody(
    filter: IssueFilter,
    lazyItems: LazyPagingItems<IssueItem>,
    /** This list's latest finished network refresh; see [rememberPagedPhase]. */
    lastRefresh: ListRefresh?,
    /** What the rows' relative times are worded against; see [IssuesUiState.Ready.now]. */
    now: Long,
    onOpen: (IssueItem) -> Unit,
    /** A pull, beside the list's own refresh: what else on the page it refreshes. */
    onPull: () -> Unit,
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
                        RefreshFailedLine(R.string.issues_refresh_failed, onRetry = lazyItems::retry)
                        IssueList(lazyItems, now, onOpen, contentPadding.belowPinnedLine())
                    }
                } else {
                    IssueList(lazyItems, now, onOpen, contentPadding)
                }
            PagedPhase.Skeleton ->
                ListRowSkeletonColumn(
                    contentPadding = contentPadding.filteredPagePadding(),
                    modifier = Modifier.fillMaxSize(),
                    height = dimensionResource(R.dimen.issues_row_skeleton_height),
                    fontScaleHeights = issueRowSkeletonHeights(),
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
                        icon = Icons.Filled.ReportProblem,
                    )
                }
        }
    }
}

@Composable
private fun IssueList(
    lazyItems: LazyPagingItems<IssueItem>,
    now: Long,
    onOpen: (IssueItem) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding.filteredPagePadding(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
    ) {
        items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
            lazyItems[index]?.let { item ->
                IssueRow(
                    item = item,
                    onClick = { onOpen(item) },
                    modifier = Modifier.layoutAnchorIf(index == 0, LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
                    now = now,
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
 * One issue on the shared media row: who filed it and when above the title, the part of the show it affects after the title, then one line
 * of state, type, kind, year, rating and genre, and the opening line last.
 */
@Composable
internal fun IssueRow(
    item: IssueItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val separator = stringResource(R.string.hub_meta_separator)
    val subtitle =
        listOfNotNull(
            stringResource(item.type.labelRes()),
            stringResource(item.mediaType.labelRes()),
            item.year,
        ).joinToString(separator)
    MediaResultRow(
        result =
            MediaResult(
                key = item.id.toString(),
                kind = MediaResultKind.Poster,
                imageUrl = item.posterUrl,
                name = item.title ?: stringResource(item.mediaType.labelRes()),
                subtitle = subtitle,
                rating = item.rating,
                meta = item.genre,
                blurb = item.problem,
                nameSuffix = issueAffectedLabel(item, R.string.issue_affected_short),
                statusInline = true,
            ),
        onClick = onClick,
        modifier = modifier,
        trailing = { RequestStateChip(label = stringResource(item.status.labelRes()), tone = item.status.tone()) },
        byline = { IssueByline(item, now) },
    )
}

@Composable
private fun IssueByline(
    item: IssueItem,
    now: Long,
) {
    val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
    Row(verticalAlignment = Alignment.CenterVertically) {
        item.reportedBy?.let { reporter ->
            BingeInitialsAvatar(name = reporter, avatarUrl = item.reportedByAvatarUrl)
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

/**
 * The part of a show an issue affects; a movie is always the whole film, so it carries none. [episodeRes] words a season and an episode:
 * the long form by default, the short "S2 E3" where it follows a title.
 */
@Composable
internal fun issueAffectedLabel(
    item: IssueItem,
    @StringRes episodeRes: Int = R.string.issue_affected_episode,
): String? {
    val season = item.problemSeason?.takeIf { it > 0 && item.mediaType == RequestMediaType.Tv } ?: return null
    val episode = item.problemEpisode?.takeIf { it > 0 }
    return if (episode != null) {
        stringResource(episodeRes, season, episode)
    } else {
        stringResource(R.string.request_season_number, season)
    }
}

/** What an issue row measures at the font scales it wraps in steps at: one point for each, so the skeleton follows all three. */
@Composable
private fun issueRowSkeletonHeights(): List<FontScaleHeight> =
    listOf(
        FontScaleHeight(1f, dimensionResource(R.dimen.issues_row_skeleton_height)),
        FontScaleHeight(FONT_SCALE_LARGE, dimensionResource(R.dimen.issues_row_skeleton_height_large)),
        FontScaleHeight(FONT_SCALE_LARGEST, dimensionResource(R.dimen.issues_row_skeleton_height_largest)),
    )

private const val FONT_SCALE_LARGE = 1.3f
private const val FONT_SCALE_LARGEST = 2f
