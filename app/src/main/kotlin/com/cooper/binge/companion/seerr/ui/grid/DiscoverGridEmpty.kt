package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenAction
import com.cooper.binge.companion.seerr.R

/**
 * What a grid with nothing in it says: an empty watchlist says what fills it, filters that match nothing offer to clear the
 * ones the user added, and a list that is empty by itself says so.
 */
@Composable
internal fun GridEmpty(
    state: DiscoverGridUiState.Empty,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.reason) {
        GridEmptyReason.Watchlist ->
            EmptyScreen(
                message = stringResource(R.string.grid_empty_watchlist_message),
                title = stringResource(R.string.grid_empty_watchlist_title),
                icon = Icons.Filled.BookmarkBorder,
                modifier = modifier,
            )
        GridEmptyReason.Filtered ->
            EmptyScreen(
                message = stringResource(R.string.grid_empty_message),
                title = stringResource(R.string.grid_empty_title),
                icon = Icons.Filled.FilterAltOff,
                action = ScreenAction(stringResource(R.string.grid_empty_clear_filters), onResetFilters),
                modifier = modifier,
            )
        GridEmptyReason.Nothing ->
            EmptyScreen(
                message = stringResource(R.string.grid_empty_plain_message),
                title = stringResource(R.string.grid_empty_title),
                icon = Icons.Filled.SearchOff,
                modifier = modifier,
            )
    }
}
