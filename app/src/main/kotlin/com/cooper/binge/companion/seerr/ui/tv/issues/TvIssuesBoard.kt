package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.tv.template.TvHubRow
import com.binge.designsystem.tv.template.TvImmersiveHub
import com.binge.designsystem.tv.template.TvMessagePage
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.issues.emptyMessageRes
import com.cooper.binge.companion.seerr.ui.issues.issueAffectedLabel
import com.cooper.binge.companion.seerr.ui.issues.labelRes
import com.cooper.binge.companion.seerr.ui.issues.tone
import com.cooper.binge.companion.seerr.ui.tv.TV_ROW_ITEM_CAP
import com.cooper.binge.companion.seerr.ui.tv.TvBackdropArtwork
import com.cooper.binge.companion.seerr.ui.tv.TvBackdropCopy
import com.cooper.binge.companion.seerr.ui.tv.TvHubLoading
import com.cooper.binge.companion.seerr.ui.tv.TvLocalNetworkDeniedPage
import com.cooper.binge.companion.seerr.ui.tv.TvPagedRows
import com.cooper.binge.companion.seerr.ui.tv.TvPosterCard
import com.cooper.binge.companion.seerr.ui.tv.TvRowsFallback
import com.cooper.binge.companion.seerr.ui.tv.hubFailure
import com.cooper.binge.companion.seerr.ui.tv.tvColor
import com.binge.designsystem.tv.R as TvR
import com.cooper.binge.companion.seerr.ui.requests.labelRes as mediaTypeLabelRes

/** Everything the issues board can ask of its ViewModel, in one place so the entry stays a wiring. */
internal class TvIssuesActions(
    val onOpenDetail: (IssueItem) -> Unit,
    val onSeeAll: (IssueFilter) -> Unit,
    val onRetryLoad: () -> Unit,
    val onRetryScope: () -> Unit,
)

/**
 * The issues browser as a television board: the filters as a band, the selected filter's rows beneath. OK on an issue
 * opens its sheet over the bar, where its actions and its thread are, whoever is looking; what the viewer may do is the
 * sheet's to offer (#1036).
 *
 * When the sheet or a see-all grid closes, the shell hands focus back into this board and the hub's own memory lands it on
 * the card or tile that opened it.
 */
@Composable
internal fun TvIssuesBoard(
    state: IssuesUiState,
    rowsFor: (IssueFilter) -> TvPagedRows<IssueItem>,
    actions: TvIssuesActions,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is IssuesUiState.Ready -> TvIssuesRows(ready = state, rowsFor = rowsFor, actions = actions, now = now)
            else -> TvIssuesUnresolved(state, actions)
        }
    }
}

/** What the board shows before the issues are known: the loading page, or that they could not be read, with a retry. */
@Composable
private fun TvIssuesUnresolved(
    state: IssuesUiState,
    actions: TvIssuesActions,
) {
    if (state !is IssuesUiState.Error) {
        TvHubLoading()
        return
    }
    if (state.error == SeerrError.LocalNetworkDenied) {
        TvLocalNetworkDeniedPage(onRetry = actions.onRetryScope)
        return
    }
    TvMessagePage(
        body = stringResource(R.string.tv_list_load_failed),
        icon = Icons.Filled.Warning,
        primary = ScreenAction(stringResource(R.string.hub_retry), actions.onRetryScope),
    )
}

/** The filters that get a row; "All" would only repeat the others. */
internal val IssueRowFilters = IssueFilter.entries.filter { it != IssueFilter.All }

/** The issues as the design system's immersive hub: a row of posters per filter over a backdrop describing the focused issue. */
@Composable
private fun TvIssuesRows(
    ready: IssuesUiState.Ready,
    rowsFor: (IssueFilter) -> TvPagedRows<IssueItem>,
    actions: TvIssuesActions,
    now: Long,
) {
    val perFilter = IssueRowFilters.map { it to rowsFor(it) }
    if (perFilter.none { (_, rows) -> rows.count > 0 }) {
        TvRowsFallback(
            rows = perFilter.map { it.second },
            emptyBody = stringResource(IssueFilter.All.emptyMessageRes()),
            onRetryLoad = actions.onRetryLoad,
        )
        return
    }
    val hubRows =
        perFilter.map { (filter, rows) ->
            TvHubRow(
                key = filter.name,
                title = filterLabel(filter, ready.counts),
                items = (0 until minOf(rows.count, TV_ROW_ITEM_CAP)).mapNotNull { rows.at(it) },
                onSeeAll = { actions.onSeeAll(filter) }.takeIf { (ready.counts?.countFor(filter) ?: rows.count) > TV_ROW_ITEM_CAP },
                failure = rows.hubFailure(actions.onRetryLoad),
            )
        }
    TvImmersiveHub(
        rows = hubRows,
        itemId = { it.id },
        cardWidth = dimensionResource(TvR.dimen.tv_immersive_card_width),
        onItemClick = actions.onOpenDetail,
        seeAllLabel = stringResource(R.string.tv_see_all),
        artwork = { item -> TvBackdropArtwork(item.backdropUrl, item.posterUrl) },
        copy = { item -> TvIssueCopy(item, now) },
    ) { item, isFocused, onFocusChanged, onClick, cellModifier ->
        TvPosterCard(
            title = item.title ?: stringResource(item.mediaType.mediaTypeLabelRes()),
            posterUrl = item.posterUrl,
            isFocused = isFocused,
            onFocusChanged = onFocusChanged,
            enabled = item.id !in ready.actingIds,
            onClick = onClick,
            modifier = cellModifier,
        )
    }
}

/** What the backdrop says about the focused issue: the title, what is wrong and where, who filed it, and the opening line. */
@Composable
internal fun ColumnScope.TvIssueCopy(
    item: IssueItem,
    now: Long,
) {
    val separator = stringResource(R.string.hub_meta_separator)
    TvBackdropCopy(
        meta =
            listOfNotNull(
                stringResource(item.mediaType.mediaTypeLabelRes()),
                item.year,
                item.certification,
            ).joinToString(separator),
        title = item.title ?: stringResource(item.mediaType.mediaTypeLabelRes()),
        status =
            listOfNotNull(
                stringResource(item.status.labelRes()),
                stringResource(item.type.labelRes()),
                issueAffectedLabel(item),
                item.reportedBy ?: stringResource(R.string.requests_requester_unknown),
                item.commentCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.issue_comments, it, it) },
                formatRelativeOrAbsolute(item.createdAtMillis, now),
            ).joinToString(separator),
        statusColor = item.status.tone().tvColor(),
        // What the reporter wrote is the point of an issue; the title's synopsis only fills in when there is none.
        synopsis = item.problem ?: item.overview,
    )
}

@Composable
private fun filterLabel(
    filter: IssueFilter,
    counts: IssueCounts?,
): String {
    val label = stringResource(filter.labelRes())
    val count = counts?.countFor(filter) ?: return label
    return stringResource(R.string.tv_filter_with_count, label, count)
}
