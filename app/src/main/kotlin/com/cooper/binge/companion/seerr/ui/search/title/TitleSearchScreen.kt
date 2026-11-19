package com.cooper.binge.companion.seerr.ui.search.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.CenteredContent
import com.binge.designsystem.CollapsingTitleState
import com.binge.designsystem.collapsingTitle
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.navOverlayPadding
import com.binge.designsystem.navOverlayStart
import com.binge.designsystem.nestedScrollConnection
import com.binge.designsystem.searchResultColumns
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import com.binge.designsystem.R as DesR

/** What the screen does with what the user does; the entry wires each to the view model. */
internal class TitleSearchActions(
    val onQueryChange: (String) -> Unit,
    val onClear: () -> Unit,
    val onSubmit: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onRecent: (String) -> Unit,
    val onForgetRecents: () -> Unit,
    val onOpenTitle: (TitleResult) -> Unit,
    val onType: (SearchType) -> Unit,
    val onOpenGrid: (HubGrid, String) -> Unit,
)

/**
 * The Search tab, laid out as Binge's: a large title that gives way as the results scroll, the field and the type chips, and
 * below them whichever of the idle offer, results or a message the state calls for. The title starts at the status bar, with no bar
 * of its own above it, as this is a tab root with nowhere to go back to.
 */
@Composable
internal fun TitleSearchScreen(
    state: TitleSearchUiState,
    actions: TitleSearchActions,
    modifier: Modifier = Modifier,
) {
    val titleState = remember { CollapsingTitleState() }
    CenteredContent(modifier, maxWidth = dimensionResource(DesR.dimen.search_content_max_width)) { capped ->
        Column(capped.nestedScroll(remember(titleState) { titleState.nestedScrollConnection() })) {
            SearchHeader(state, actions, titleState)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    is TitleSearchUiState.Idle ->
                        IdleBody(
                            state,
                            actions,
                            navOverlayPadding(PaddingValues(), bleedStart = true),
                        )
                    is TitleSearchUiState.Loading -> LoadingScreen()
                    is TitleSearchUiState.Ready ->
                        key(
                            state.type,
                        ) { ResultsList(state, actions, navOverlayPadding(PaddingValues(dimensionResource(DesR.dimen.padding_m)))) }
                    is TitleSearchUiState.NoResults -> NoResultsBody(state, actions.onOpenGrid)
                    is TitleSearchUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    state: TitleSearchUiState,
    actions: TitleSearchActions,
    titleState: CollapsingTitleState,
) {
    Column(Modifier.statusBarsPadding().padding(start = navOverlayStart())) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset))
                .padding(top = dimensionResource(DesR.dimen.padding_s), bottom = dimensionResource(DesR.dimen.padding_m)),
        ) {
            // Only the title gives way: a field that could leave the window would make the user scroll back to change the query.
            Column(Modifier.collapsingTitle(titleState)) {
                Text(
                    text = stringResource(R.string.title_search_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            }
            BingeSearchField(
                query = state.text,
                onQueryChange = actions.onQueryChange,
                onClear = actions.onClear,
                onSubmit = actions.onSubmit,
                placeholder = stringResource(R.string.title_search_placeholder),
            )
        }
        when (state) {
            is TitleSearchUiState.Ready -> TypeChips(state.type, state.counts, actions.onType)
            is TitleSearchUiState.NoResults -> TypeChips(state.type, SearchType.entries.associateWith { 0 }, actions.onType)
            else -> Unit
        }
    }
}

@Composable
private fun TypeChips(
    type: SearchType,
    counts: Map<SearchType, Int>,
    onType: (SearchType) -> Unit,
) {
    BingeFilterChipRow(
        items = SearchType.entries.map { FilterChipItem(label = stringResource(it.labelRes()), count = counts[it]) },
        selectedIndex = type.ordinal,
        onSelect = { onType(SearchType.entries[it]) },
    )
}

@StringRes
private fun SearchType.labelRes(): Int =
    when (this) {
        SearchType.All -> R.string.title_search_type_all
        SearchType.Movies -> R.string.hubs_title_movies
        SearchType.Series -> R.string.hubs_title_tv
        SearchType.People -> R.string.title_search_type_people
    }

@Composable
private fun ResultsList(
    state: TitleSearchUiState.Ready,
    actions: TitleSearchActions,
    contentPadding: PaddingValues,
) {
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(gridState, state.hasMore, state.loaded) {
        if (state.hasMore) snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { actions.onLoadMore() }
    }
    if (state.results.isEmpty() && !state.hasMore && !state.appending) {
        EmptyScreen(
            message = stringResource(R.string.title_search_type_none_message, state.text.trim()),
            title = stringResource(R.string.title_search_type_none_title),
        )
        return
    }
    // One column on a phone; the wider windows fold the results into columns rather than running a row the width of a tablet.
    // The grid is capped at the search width, not the reading column, and centred above it.
    CenteredContent(maxWidth = dimensionResource(DesR.dimen.search_content_max_width)) { capped ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(searchResultColumns()),
            state = gridState,
            modifier = capped,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            items(state.results, key = { "${it.kind}-${it.tmdbId}" }) { result ->
                TitleResultRow(result, state.genreNames, onClick = { actions.onOpenTitle(result) })
            }
            if (state.appending || state.appendFailed || state.results.isEmpty()) {
                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { AppendFooter(state.appendFailed, actions.onLoadMore) }
            }
        }
    }
}

private const val LOAD_MORE_AHEAD = 3

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
                    stringResource(R.string.title_search_more_failed),
                    modifier = Modifier.padding(start = dimensionResource(DesR.dimen.padding_s)),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            CircularProgressIndicator()
        }
    }
}
