package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.formatRanges
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.template.TvHubRow
import com.binge.designsystem.tv.template.TvImmersiveHub
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageAction
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.requests.statusChip
import io.github.scottcooper92.binge.seerr.ui.tv.TV_ROW_ITEM_CAP
import io.github.scottcooper92.binge.seerr.ui.tv.TvBackdropArtwork
import io.github.scottcooper92.binge.seerr.ui.tv.TvBackdropCopy
import io.github.scottcooper92.binge.seerr.ui.tv.TvHubLoading
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.TvPosterCard
import io.github.scottcooper92.binge.seerr.ui.tv.TvRowsFallback
import io.github.scottcooper92.binge.seerr.ui.tv.rememberOverlayFocusRestore
import io.github.scottcooper92.binge.seerr.ui.tv.tvColor
import com.binge.designsystem.tv.R as TvR

/** Everything the requests hub can ask of its host, in one place so the entry stays a wiring. */
internal class TvRequestsActions(
    val onOpenDetail: (RequestItem) -> Unit,
    val onSeeAll: (RequestFilter) -> Unit,
    val onRetryLoad: () -> Unit,
    val onRetryScope: () -> Unit,
)

/** The filters that get a row; "All" would only repeat the others, so the rows are the tabs. */
internal val RequestRowFilters = RequestFilter.entries.filter { it != RequestFilter.All }

/** A row shows its first requests; past that the row's see-all tile opens the paged grid. */
private const val PERCENT = 100

/**
 * The requests browser as the design system's immersive hub: a row of poster cards per filter over a backdrop that
 * follows focus and describes the focused request. [rowsFor] gives each filter's paged rows; a filter with nothing
 * in it has no row, and one with more than a row holds ends in a see-all tile that opens the paged grid. OK on a
 * card opens the request's detail page, an overlay above the rail that this board does not own.
 *
 * [openRequestId] is the id of the request whose page is showing, and [seeAllOpen] whether a grid is: when both are
 * gone the board offers focus back to the card or the see-all tile that opened them.
 */
@Composable
internal fun TvRequestsRowsBoard(
    state: RequestsUiState,
    rowsFor: (RequestFilter) -> TvPagedRows<RequestItem>,
    actions: TvRequestsActions,
    modifier: Modifier = Modifier,
    openRequestId: Int? = null,
    seeAllOpen: Boolean = false,
) {
    val ready = state as? RequestsUiState.Ready
    val restore = rememberOverlayFocusRestore(overlayOpen = openRequestId != null || seeAllOpen)
    if (ready == null) {
        TvRequestsUnresolved(state, actions, modifier)
        return
    }
    val perFilter = RequestRowFilters.map { it to rowsFor(it) }
    if (perFilter.none { (_, rows) -> rows.count > 0 }) {
        TvRowsFallback(
            rows = perFilter.map { it.second },
            emptyBody = stringResource(R.string.requests_empty_all),
            onRetryLoad = actions.onRetryLoad,
            modifier = modifier,
        )
        return
    }
    val hubRows =
        perFilter.map { (filter, rows) ->
            val label = stringResource(filter.labelRes())
            val count = ready.counts?.countFor(filter)
            TvHubRow(
                key = filter.name,
                title = if (count != null) stringResource(R.string.tv_filter_with_count, label, count) else label,
                // Reading the item is what pages it in.
                items = (0 until minOf(rows.count, TV_ROW_ITEM_CAP)).mapNotNull { rows.at(it) },
                onSeeAll =
                    {
                        restore.leavingFromSeeAll(filter.name)
                        actions.onSeeAll(filter)
                    }.takeIf { (count ?: rows.count) > TV_ROW_ITEM_CAP },
            )
        }
    val now = System.currentTimeMillis()
    TvImmersiveHub(
        rows = hubRows,
        itemId = { it.id },
        cardWidth = dimensionResource(TvR.dimen.tv_immersive_card_width),
        onItemClick = {
            restore.leavingFromRow(it.id)
            actions.onOpenDetail(it)
        },
        seeAllLabel = stringResource(R.string.tv_see_all),
        seeAllModifier = restore::seeAllModifier,
        artwork = { item -> TvBackdropArtwork(item.backdropUrl, item.posterUrl) },
        copy = { item -> TvRequestCopy(item, now) },
        modifier = modifier,
    ) { item, isFocused, onFocusChanged, onClick, cellModifier ->
        TvPosterCard(
            title = item.title ?: stringResource(item.mediaType.labelRes()),
            posterUrl = item.posterUrl,
            isFocused = isFocused,
            onFocusChanged = onFocusChanged,
            enabled = item.id !in ready.actingIds,
            onClick = onClick,
            modifier = restore.rowModifier(item.id, cellModifier),
        )
    }
}

/** What the board shows before the requests are known: the loading page, or that they could not be read, with a retry. */
@Composable
private fun TvRequestsUnresolved(
    state: RequestsUiState,
    actions: TvRequestsActions,
    modifier: Modifier,
) {
    if (state !is RequestsUiState.Error) {
        TvHubLoading(modifier)
        return
    }
    TvMessagePage(
        body = stringResource(R.string.tv_list_load_failed),
        modifier = modifier,
        icon = Icons.Filled.Warning,
        primary = TvPageAction(stringResource(R.string.hub_retry), actions.onRetryScope),
    )
}

/** What the backdrop says about the focused request: what it is, its title, its state, who asked, and the synopsis. */
@Composable
internal fun ColumnScope.TvRequestCopy(
    item: RequestItem,
    now: Long,
) {
    val separator = stringResource(R.string.hub_meta_separator)
    val chip = item.statusChip()
    TvBackdropCopy(
        meta =
            listOfNotNull(
                stringResource(item.mediaType.labelRes()),
                item.year,
                item.certification,
                stringResource(R.string.settings_service_4k).takeIf { item.is4k },
                item.seasonNumbers
                    .takeIf { it.isNotEmpty() }
                    ?.let { pluralStringResource(R.plurals.requests_seasons, it.size, it.formatRanges()) },
            ).joinToString(separator),
        title = item.title ?: stringResource(item.mediaType.labelRes()),
        status =
            listOfNotNull(
                stringResource(chip.labelRes),
                item.download?.takeIf { it.downloading }?.let {
                    stringResource(
                        R.string.tv_download_percent,
                        (it.fraction * PERCENT).toInt(),
                    )
                },
                item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                formatRelativeOrAbsolute(item.requestedAtMillis, now),
            ).joinToString(separator),
        statusColor = chip.tone.tvColor(),
        synopsis = item.overview,
    )
}
