package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.PagedGridCallbacks
import com.binge.designsystem.template.PagedGridScreen
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.ui.hub.AccountBar
import com.cooper.binge.companion.seerr.ui.hub.TitleBarAccount
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import java.time.LocalDate

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
    val onResetFilters: () -> Unit = {},
)

/**
 * The "see all" grid: a bar of the order and the filter the row started with, over a paged grid of posters, drawn by the design
 * system's [PagedGridScreen]. [firstVisibleIndex] starts it scrolled, for a frame of the grid mid-list. [reselectKey] is the tab this
 * grid is the root of: a tap on that tab again scrolls it to the top. A grid opened from a row has none.
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
    reselectKey: Any? = null,
) {
    val controls = state.controls
    val cards = (state as? DiscoverGridUiState.Ready)?.cards.orEmpty()
    val error = (state as? DiscoverGridUiState.Error)?.error
    PagedGridScreen(
        title = stringResource(R.string.grid_title),
        state = state.toPagedGridState(actions.onResetFilters),
        callbacks =
            PagedGridCallbacks(
                onOpenItem = { key -> cards.firstOrNull { it.gridKey() == key }?.let(actions.onOpenTitle) },
                onLoadMore = actions.onLoadMore,
                onRetry = actions.onRetry,
                onClearChip = { key -> controls.clearChip(key, actions) },
                onBack = actions.onBack,
            ),
        subtitle = controls.subtitle(kind, title),
        filterBar = controls.toFilterBar(remember { LocalDate.now() }),
        reselectKey = reselectKey,
        firstVisibleIndex = firstVisibleIndex,
        loadingLabel = stringResource(R.string.grid_loading),
        moreFailedLabel = stringResource(R.string.grid_more_failed),
        titleTrailing = accountBar?.let { bar -> { interactive -> TitleBarAccount(bar, interactive, onOpenAccount) } },
        barActions = { handedOver, fraction -> accountBar?.let { TitleBarAccount(it, handedOver, onOpenAccount, fraction) } },
        failedContent = error?.let { failure -> { modifier -> ErrorScreen(failure, modifier, onRetry = actions.onRetry) } },
        filterSheet = { onDismiss -> FilterSheetHost(kind, controls, actions, onDismiss) },
    )
}

/** The sheet that holds the order and the filters, opened from the title row's pill or the collapsed bar's action. */
@Composable
private fun FilterSheetHost(
    kind: TitleKind,
    controls: GridControls,
    actions: DiscoverGridActions,
    onDismiss: () -> Unit,
) {
    if (controls.sort != null) {
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

/** What the grid lists and in what order: the preset or slider's name, then the order. The title says "Discover", as Binge's does. */
@Composable
internal fun GridControls.subtitle(
    kind: TitleKind,
    name: String?,
): String {
    val label = name ?: stringResource(if (kind == TitleKind.Series) R.string.hubs_title_tv else R.string.hubs_title_movies)
    return sort?.let { stringResource(R.string.grid_subtitle, label, stringResource(it.labelRes())) } ?: label
}
