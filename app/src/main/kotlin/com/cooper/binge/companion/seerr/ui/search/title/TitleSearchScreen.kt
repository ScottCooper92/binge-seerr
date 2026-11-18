package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.navOverlayPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleResult
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
)

/** The Search tab: the field above, and below it whichever of recents, results or a message the state calls for. */
@Composable
internal fun TitleSearchScreen(
    state: TitleSearchUiState,
    actions: TitleSearchActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        BingeSearchField(
            query = state.text,
            onQueryChange = actions.onQueryChange,
            onClear = actions.onClear,
            onSubmit = actions.onSubmit,
            placeholder = stringResource(R.string.title_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is TitleSearchUiState.Idle -> IdleBody(state, actions)
                is TitleSearchUiState.Loading -> LoadingScreen()
                is TitleSearchUiState.Ready -> ResultsList(state, actions)
                is TitleSearchUiState.NoResults ->
                    EmptyScreen(
                        message = stringResource(R.string.title_search_none_message, state.query),
                        title = stringResource(R.string.title_search_none_title),
                    )
                is TitleSearchUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
            }
        }
    }
}

@Composable
private fun IdleBody(
    state: TitleSearchUiState.Idle,
    actions: TitleSearchActions,
) {
    if (state.recents.isEmpty()) {
        EmptyScreen(
            message = stringResource(R.string.title_search_idle_message),
            title = stringResource(R.string.title_search_idle_title),
            icon = Icons.Filled.Search,
        )
    } else {
        RecentSearches(state.recents, onSelect = actions.onRecent, onClear = actions.onForgetRecents)
    }
}

@Composable
private fun ResultsList(
    state: TitleSearchUiState.Ready,
    actions: TitleSearchActions,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(listState, state.hasMore, state.results.size) {
        if (state.hasMore) snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { actions.onLoadMore() }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = navOverlayPadding(PaddingValues(dimensionResource(DesR.dimen.padding_m))),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        items(state.results, key = { "${it.kind}-${it.tmdbId}" }) { result ->
            TitleResultRow(result, onClick = { actions.onOpenTitle(result) })
        }
        if (state.appending || state.appendFailed) item(key = "footer") { AppendFooter(state.appendFailed, actions.onLoadMore) }
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
