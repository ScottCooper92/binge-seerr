package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.DiscoverQuickClearChip
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.QuickClearTone
import com.binge.designsystem.mediaGridColumns
import com.binge.designsystem.navOverlayPadding
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.ui.hubs.toStatus
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.SortSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import com.binge.designsystem.R as DesR

/** What the screen does with what the user does; the entry wires each to the view model. */
internal class DiscoverGridActions(
    val onBack: () -> Unit,
    val onSort: (DiscoverSort) -> Unit,
    val onClearFilter: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenTitle: (HubCard) -> Unit,
)

private const val LOAD_MORE_AHEAD = 6

/** The "see all" grid: a bar of the order and the filter the row started with, over a paged grid of posters. */
@Composable
internal fun DiscoverGridScreen(
    kind: TitleKind,
    title: String?,
    state: DiscoverGridUiState,
    actions: DiscoverGridActions,
) {
    val fallback = stringResource(if (kind == TitleKind.Series) R.string.hubs_title_tv else R.string.hubs_title_movies)
    BingeScreenScaffold(
        title = title ?: fallback,
        onBack = actions.onBack,
        bar = ScreenBar.Small,
        header = { GridControlsRow(state.controls, actions) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val inner = navOverlayPadding(PaddingValues(dimensionResource(DesR.dimen.padding_m)))
            when (state) {
                is DiscoverGridUiState.Loading -> LoadingScreen()
                is DiscoverGridUiState.Empty ->
                    EmptyScreen(
                        message = stringResource(R.string.grid_empty_message),
                        title = stringResource(R.string.grid_empty_title),
                        modifier = Modifier.padding(inner),
                    )
                is DiscoverGridUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry = actions.onRetry)
                is DiscoverGridUiState.Ready -> PosterGrid(state, inner, actions)
            }
        }
    }
}

@Composable
private fun GridControlsRow(
    controls: GridControls,
    actions: DiscoverGridActions,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    if (controls.sort == null && controls.chip == null) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dimensionResource(DesR.dimen.padding_m)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        controls.sort?.let { sort ->
            item(key = "sort") {
                BingeFilterChip(
                    label = stringResource(sort.labelRes()),
                    selected = false,
                    onClick = { choosing = true },
                    leadingIcon = sort.icon(),
                )
            }
        }
        controls.chip?.let { chip ->
            item(key = "filter") {
                DiscoverQuickClearChip(
                    label = chip.label ?: stringResource(chip.kind.labelRes()),
                    tone = QuickClearTone.Include,
                    onClick = actions.onClearFilter,
                )
            }
        }
    }
    if (choosing && controls.sort != null) {
        SortSheet(
            choices = controls.sortChoices,
            selected = controls.sort,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                choosing = false
                actions.onSort(it)
            },
            onDismiss = { choosing = false },
            icon = { it.icon() },
        )
    }
}

@Composable
private fun PosterGrid(
    state: DiscoverGridUiState.Ready,
    contentPadding: PaddingValues,
    actions: DiscoverGridActions,
) {
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(gridState, state.hasMore, state.cards.size) {
        if (state.hasMore) snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { actions.onLoadMore() }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(mediaGridColumns()),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        items(state.cards, key = { it.kind.name + it.tmdbId }) { card ->
            MediaCard(
                posterUrl = card.artworkUrl,
                title = card.name,
                rating = null,
                onClick = { actions.onOpenTitle(card) },
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
