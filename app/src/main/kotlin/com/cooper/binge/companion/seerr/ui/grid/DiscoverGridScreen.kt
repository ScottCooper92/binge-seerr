package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DiscoverQuickClearChip
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.QuickClearTone
import com.binge.designsystem.component.rememberPaneTopBarScrollBehavior
import com.binge.designsystem.navOverlayPadding
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.ui.hub.AccountBar
import com.cooper.binge.companion.seerr.ui.hub.TitleBarAccount
import com.cooper.binge.companion.seerr.ui.hubs.toStatus
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.LocalDate
import com.binge.designsystem.R as DesR

/** What the screen does with what the user does; the entry wires each to the view model. */
internal class DiscoverGridActions(
    val onBack: (() -> Unit)?,
    val onClearFilter: () -> Unit,
    val onClearGroup: (FilterGroup) -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenTitle: (HubCard) -> Unit,
    val onApply: (DiscoverSort, DiscoverFilters) -> Unit,
    val onSwitchKind: (TitleKind, DiscoverSort, DiscoverFilters) -> Unit,
    val loads: FilterLoads,
)

private const val LOAD_MORE_AHEAD = 6

/** Past this much collapse the title row's pill gives way to the bar's action. */
private const val PILL_INTERACTIVE_COLLAPSE = 0.5f

/**
 * The "see all" grid: a bar of the order and the filter the row started with, over a paged grid of posters. [firstVisibleIndex] starts it
 * scrolled, for a frame of the grid mid-list.
 */
@Composable
internal fun DiscoverGridScreen(
    kind: TitleKind,
    title: String?,
    state: DiscoverGridUiState,
    actions: DiscoverGridActions,
    firstVisibleIndex: Int = 0,
    accountBar: AccountBar? = null,
    onOpenAccount: (Int) -> Unit = {},
) {
    val scrollBehavior = rememberPaneTopBarScrollBehavior()
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val collapsed = scrollBehavior.state.collapsedFraction
    val pillInteractive = collapsed < PILL_INTERACTIVE_COLLAPSE
    val activeCount = state.controls.groups.size
    val filterable = state.controls.sort != null
    BingeScreenScaffold(
        title = stringResource(R.string.grid_title),
        onBack = actions.onBack,
        bar = ScreenBar.Large,
        scrollBehavior = scrollBehavior,
        subtitle = state.controls.subtitle(kind, title),
        titleTrailing =
            if (filterable || accountBar != null) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                    ) {
                        if (filterable) FilterActionButton(activeCount, pillInteractive, onClick = { filtersOpen = true })
                        accountBar?.let { TitleBarAccount(it, pillInteractive, onOpenAccount) }
                    }
                }
            } else {
                null
            },
        actions = {
            if (filterable) CollapsedFilterAction(activeCount, !pillInteractive, collapsed) { filtersOpen = true }
            accountBar?.let { TitleBarAccount(it, !pillInteractive, onOpenAccount, collapsed) }
        },
        header =
            state.controls.takeIf { it.chip != null || it.groups.isNotEmpty() }?.let { controls ->
                { ActiveFilterChips(controls, actions) }
            },
    ) { padding ->
        // The bar's height and the navigation bar go inside the grid's own scroll, so posters pass under the one and run down to the
        // window's edge under the other, as Binge's gallery does; only the sides stay outside it.
        ScreenBody(padding) { body ->
            val inner =
                navOverlayPadding(
                    PaddingValues(
                        start = dimensionResource(DesR.dimen.screen_content_inset),
                        top = body.calculateTopPadding() + dimensionResource(DesR.dimen.padding_s),
                        end = dimensionResource(DesR.dimen.screen_content_inset),
                        bottom = body.calculateBottomPadding() + dimensionResource(DesR.dimen.padding_m),
                    ),
                )
            when (state) {
                is DiscoverGridUiState.Loading -> GridSkeleton(inner)
                is DiscoverGridUiState.Empty ->
                    EmptyScreen(
                        message = stringResource(R.string.grid_empty_message),
                        title = stringResource(R.string.grid_empty_title),
                        modifier = Modifier.padding(inner),
                    )
                is DiscoverGridUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry = actions.onRetry)
                is DiscoverGridUiState.Ready -> PosterGrid(state, inner, actions, firstVisibleIndex)
            }
        }
    }
    FilterSheetHost(kind, state.controls, actions, open = filtersOpen, onDismiss = { filtersOpen = false })
}

/** The sheet that holds the order and the filters, opened from the title row's pill or the collapsed bar's action. */
@Composable
private fun FilterSheetHost(
    kind: TitleKind,
    controls: GridControls,
    actions: DiscoverGridActions,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    if (open && controls.sort != null) {
        DiscoverFilterSheet(
            kind = kind,
            sort = controls.sort,
            filters = controls.filters,
            vocabulary = controls.vocabulary,
            today = remember { LocalDate.now() },
            loads = actions.loads,
            onApply = { next, sort, filters ->
                if (next == kind) actions.onApply(sort, filters) else actions.onSwitchKind(next, sort, filters)
            },
            onDismiss = onDismiss,
        )
    }
}

/** The filters in force, each removable, in one scrolling row under the header, as Binge's discover filter chips are. */
@Composable
private fun ActiveFilterChips(
    controls: GridControls,
    actions: DiscoverGridActions,
) {
    val today = remember { LocalDate.now() }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        controls.chip?.let { chip ->
            item(key = "filter") {
                DiscoverQuickClearChip(
                    label = chip.label ?: stringResource(chip.kind.labelRes()),
                    tone = QuickClearTone.Include,
                    onClick = actions.onClearFilter,
                )
            }
        }
        items(controls.groups, key = { it.name }) { group ->
            val section = DiscoverFilterSection.entries.first { it.group == group }
            DiscoverQuickClearChip(
                label = section.summary(controls.added, controls.vocabulary, today),
                tone = QuickClearTone.Include,
                onClick = { actions.onClearGroup(group) },
            )
        }
    }
}

/** What the grid lists and in what order: the preset or slider's name, then the order. The title says "Discover", as Binge's does. */
@Composable
private fun GridControls.subtitle(
    kind: TitleKind,
    name: String?,
): String {
    val label = name ?: stringResource(if (kind == TitleKind.Series) R.string.hubs_title_tv else R.string.hubs_title_movies)
    return sort?.let { stringResource(R.string.grid_subtitle, label, stringResource(it.labelRes())) } ?: label
}

@Composable
private fun PosterGrid(
    state: DiscoverGridUiState.Ready,
    contentPadding: PaddingValues,
    actions: DiscoverGridActions,
    firstVisibleIndex: Int,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = firstVisibleIndex)
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(gridState, state.hasMore, state.cards.size) {
        if (state.hasMore) snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { actions.onLoadMore() }
    }
    GridFrame(contentPadding, gridState) {
        itemsIndexed(state.cards, key = { _, card -> card.kind.name + card.tmdbId }) { index, card ->
            MediaCard(
                posterUrl = card.artworkUrl,
                title = card.name,
                rating = card.rating,
                onClick = { actions.onOpenTitle(card) },
                modifier = Modifier.firstCellAnchor(index),
                status = card.availability.toStatus(),
            )
        }
        if (state.appending || state.appendFailed) {
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { AppendFooter(state.appendFailed, actions.onLoadMore) }
        }
    }
}

@Composable
private fun AppendFooter(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(vertical = dimensionResource(DesR.dimen.padding_m)), contentAlignment = Alignment.Center) {
        if (failed) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(
                    stringResource(R.string.grid_more_failed),
                    modifier = Modifier.padding(start = dimensionResource(DesR.dimen.padding_s)),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

private fun ChipKind.labelRes(): Int =
    when (this) {
        ChipKind.Upcoming -> R.string.grid_chip_upcoming
        ChipKind.Genre -> R.string.grid_chip_genre
        ChipKind.Company -> R.string.grid_chip_company
        ChipKind.Language -> R.string.grid_chip_language
        ChipKind.Keyword -> R.string.grid_chip_keyword
        ChipKind.Provider -> R.string.grid_chip_provider
    }
