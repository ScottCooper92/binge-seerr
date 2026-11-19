package com.cooper.binge.companion.seerr.ui.search.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.CenteredContent
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.OnTabReselect
import com.binge.designsystem.component.rememberPaneTopBarScrollBehavior
import com.binge.designsystem.navOverlayPadding
import com.binge.designsystem.searchResultColumns
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.StudioResult
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.ui.hub.AccountBar
import com.cooper.binge.companion.seerr.ui.hub.TitleBarAccount
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
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
    val onOpenStudio: (StudioResult) -> Unit,
)

/**
 * The Search tab on the design system's large bar: the title gives way as the results scroll, and the field and the type chips stay
 * pinned under it as its header. The account sits beside the title and in the collapsed bar, as on the Discover grid. Below the bar
 * is whichever of the idle offer, results or a message the state calls for.
 */
@Composable
internal fun TitleSearchScreen(
    state: TitleSearchUiState,
    actions: TitleSearchActions,
    modifier: Modifier = Modifier,
    accountBar: AccountBar? = null,
    onOpenAccount: (Int) -> Unit = {},
) {
    val scrollBehavior = rememberPaneTopBarScrollBehavior()
    val collapsed = scrollBehavior.state.collapsedFraction
    val titleRowShows = collapsed < TITLE_ROW_COLLAPSE
    BingeScreenScaffold(
        title = stringResource(R.string.title_search_title),
        modifier = modifier,
        bar = ScreenBar.Large,
        scrollBehavior = scrollBehavior,
        titleTrailing = accountBar?.let { { TitleBarAccount(it, titleRowShows, onOpenAccount) } },
        actions = { accountBar?.let { TitleBarAccount(it, !titleRowShows, onOpenAccount, collapsed) } },
        header = { SearchControls(state, actions) },
    ) { padding ->
        ScreenBody(padding) { body ->
            val top = body.calculateTopPadding()
            CenteredContent(maxWidth = dimensionResource(DesR.dimen.search_content_max_width)) { capped ->
                Box(capped.fillMaxSize()) { SearchBody(state, actions, body, top) }
            }
        }
    }
}

/** Past this much collapse the title row's account gives way to the bar's. */
private const val TITLE_ROW_COLLAPSE = 0.5f

@Composable
private fun SearchBody(
    state: TitleSearchUiState,
    actions: TitleSearchActions,
    body: PaddingValues,
    top: Dp,
) {
    val bottom = body.calculateBottomPadding()
    when (state) {
        is TitleSearchUiState.Idle ->
            IdleBody(state, actions, navOverlayPadding(PaddingValues(top = top, bottom = bottom), bleedStart = true))
        is TitleSearchUiState.Loading -> LoadingScreen(Modifier.padding(top = top))
        is TitleSearchUiState.Ready ->
            key(state.type) {
                ResultsList(
                    state,
                    actions,
                    navOverlayPadding(
                        PaddingValues(
                            start = dimensionResource(DesR.dimen.padding_m),
                            top = top + dimensionResource(DesR.dimen.padding_s),
                            end = dimensionResource(DesR.dimen.padding_m),
                            bottom = bottom + dimensionResource(DesR.dimen.padding_m),
                        ),
                    ),
                )
            }
        is TitleSearchUiState.NoResults -> NoResultsBody(state, actions.onOpenGrid, Modifier.fillMaxSize().padding(top = top))
        is TitleSearchUiState.Error ->
            ErrorScreen(
                error = state.error,
                modifier = Modifier.fillMaxSize().padding(top = top),
                onRetry = actions.onRetry,
            )
    }
}

/** The field and, once there is something to filter, the type chips: the bar's header, pinned while the results scroll. */
@Composable
private fun SearchControls(
    state: TitleSearchUiState,
    actions: TitleSearchActions,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    OnTabReselect(SeerrTab.Search) {
        focus.requestFocus()
        keyboard?.show()
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = dimensionResource(DesR.dimen.search_content_max_width)).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset))
                    .padding(bottom = dimensionResource(DesR.dimen.padding_m)),
            ) {
                BingeSearchField(
                    query = state.text,
                    onQueryChange = actions.onQueryChange,
                    onClear = actions.onClear,
                    onSubmit = actions.onSubmit,
                    placeholder = stringResource(R.string.title_search_placeholder),
                    focusRequester = focus,
                )
            }
            when (state) {
                is TitleSearchUiState.Ready -> TypeChips(state.type, state.counts, state.hasStudios, actions.onType)
                is TitleSearchUiState.NoResults ->
                    TypeChips(state.type, SearchType.entries.associateWith { 0 }, state.hasStudios, actions.onType)
                else -> Unit
            }
        }
    }
}

@Composable
private fun TypeChips(
    type: SearchType,
    counts: Map<SearchType, Int>,
    hasStudios: Boolean,
    onType: (SearchType) -> Unit,
) {
    val types = SearchType.entries.filter { hasStudios || it != SearchType.Studios }
    BingeFilterChipRow(
        items = types.map { FilterChipItem(label = stringResource(it.labelRes()), count = counts[it]) },
        selectedIndex = types.indexOf(type).coerceAtLeast(0),
        onSelect = { onType(types[it]) },
    )
}

@StringRes
private fun SearchType.labelRes(): Int =
    when (this) {
        SearchType.All -> R.string.title_search_type_all
        SearchType.Movies -> R.string.hubs_title_movies
        SearchType.Series -> R.string.hubs_title_tv
        SearchType.People -> R.string.title_search_type_people
        SearchType.Studios -> R.string.title_search_type_studios
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
    val onStudios = state.type == SearchType.Studios
    val empty = if (onStudios) state.studios.isEmpty() else state.results.isEmpty()
    val settled = !state.hasMore && !state.appending && !state.appendFailed
    if (empty && settled) {
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
            if (onStudios) {
                items(state.studios, key = { "studio-${it.id}" }) { studio ->
                    StudioResultRow(studio, onClick = { actions.onOpenStudio(studio) })
                }
            } else {
                items(state.results, key = { "${it.kind}-${it.tmdbId}" }) { result ->
                    TitleResultRow(result, state.genreNames, onClick = { actions.onOpenTitle(result) })
                }
            }
            if (state.appending || state.appendFailed || empty) {
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
