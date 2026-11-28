package com.cooper.binge.companion.seerr.ui.grid

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.QuickClearTone
import com.binge.designsystem.template.GridFilterBar
import com.binge.designsystem.template.GridFilterChip
import com.binge.designsystem.template.GridItem
import com.binge.designsystem.template.PagedGridState
import com.binge.designsystem.template.ScreenAction
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.ui.hubs.toStatus
import com.cooper.binge.companion.seerr.ui.state.toErrorKind
import java.time.LocalDate

/** The key of the preset's own chip; the added filters are keyed by their group's name. */
private const val PRESET_CHIP_KEY = "filter"

/** A card's key in the grid: what the app calls it, text so a lazy grid can save it. */
fun HubCard.gridKey(): String = kind.name + tmdbId

/** The grid's state as the design system's grid draws it. A failure is drawn by the screen's own words, so only its kind is passed. */
@Composable
fun DiscoverGridUiState.toPagedGridState(onResetFilters: () -> Unit): PagedGridState =
    when (this) {
        is DiscoverGridUiState.Loading -> PagedGridState.Loading
        is DiscoverGridUiState.Ready ->
            PagedGridState.Ready(
                items = cards.map { it.toGridItem() },
                hasMore = hasMore,
                appending = appending,
                appendFailed = appendFailed,
            )
        is DiscoverGridUiState.Empty -> reason.toEmptyState(onResetFilters)
        is DiscoverGridUiState.Error -> PagedGridState.Failed(kind = error.toErrorKind())
    }

@Composable
private fun HubCard.toGridItem(): GridItem =
    GridItem(key = gridKey(), posterUrl = artworkUrl, title = name, rating = rating, status = availability.toStatus())

/**
 * What a grid with nothing in it says: an empty watchlist says what fills it, filters that match nothing offer to clear the
 * ones the user added, and a list that is empty by itself says so.
 */
@Composable
private fun GridEmptyReason.toEmptyState(onResetFilters: () -> Unit): PagedGridState.Empty =
    when (this) {
        GridEmptyReason.Watchlist ->
            PagedGridState.Empty(
                title = stringResource(R.string.grid_empty_watchlist_title),
                message = stringResource(R.string.grid_empty_watchlist_message),
                icon = Icons.Filled.BookmarkBorder,
            )
        GridEmptyReason.Filtered ->
            PagedGridState.Empty(
                title = stringResource(R.string.grid_empty_title),
                message = stringResource(R.string.grid_empty_message),
                icon = Icons.Filled.FilterAltOff,
                action = ScreenAction(stringResource(R.string.grid_empty_clear_filters), onResetFilters),
            )
        GridEmptyReason.Nothing ->
            PagedGridState.Empty(
                title = stringResource(R.string.grid_empty_title),
                message = stringResource(R.string.grid_empty_plain_message),
                icon = Icons.Filled.SearchOff,
            )
    }

/** The Filters pill and the filters in force as chips, or null where the list takes no order and so no filters. */
@Composable
fun GridControls.toFilterBar(today: LocalDate): GridFilterBar? {
    if (sort == null) return null
    val preset = chip?.let { GridFilterChip(PRESET_CHIP_KEY, it.label ?: stringResource(it.kind.labelRes())) }
    val groupChips =
        groups.map { group ->
            val section = DiscoverFilterSection.entries.first { it.group == group }
            GridFilterChip(
                group.name,
                section.summary(this.added, vocabulary, today),
                tone = if (group == FilterGroup.ExcludedKeywords) QuickClearTone.Exclude else QuickClearTone.Include,
            )
        }
    return GridFilterBar(
        activeCount = groups.size,
        pillLabel = stringResource(R.string.grid_filters_button),
        pillDescription =
            if (groups.isEmpty()) {
                stringResource(R.string.grid_open_filters)
            } else {
                pluralStringResource(R.plurals.grid_open_filters_applied, groups.size, groups.size)
            },
        chips = listOfNotNull(preset) + groupChips,
    )
}

/** A chip's removal: the preset's own filter, or one group the user added. */
fun GridControls.clearChip(
    key: String,
    actions: DiscoverGridActions,
) {
    if (key == PRESET_CHIP_KEY) actions.onClearFilter() else groups.firstOrNull { it.name == key }?.let(actions.onClearGroup)
}

@StringRes
private fun ChipKind.labelRes(): Int =
    when (this) {
        ChipKind.Upcoming -> R.string.grid_chip_upcoming
        ChipKind.Genre -> R.string.grid_chip_genre
        ChipKind.Company -> R.string.grid_chip_company
        ChipKind.Language -> R.string.grid_chip_language
        ChipKind.Keyword -> R.string.grid_chip_keyword
        ChipKind.Provider -> R.string.grid_chip_provider
    }
