package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeFilterChipPager
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.accent
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.PagedRefreshError
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

class LogsActions(
    val onBack: () -> Unit,
    val onLevelChange: (LogLevel) -> Unit,
    val onSearchChange: (String) -> Unit,
    val onFollowingChange: (Boolean) -> Unit,
    val onCopy: (String) -> Unit,
)

/**
 * The logs page: a page of paged lines per level, swiped between or picked from the chips, over a
 * search that spans them all. Each line expands to what it carried, and copies.
 *
 * @param entriesFor one cached paged list per level, so a page swiped away and back keeps its lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    state: LogsUiState,
    entriesFor: (LogLevel) -> Flow<PagingData<LogEntry>>,
    events: Flow<LogsEvent>,
    actions: LogsActions,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ScreenScaffold(
        title = stringResource(R.string.server_settings_logs),
        onBack = actions.onBack,
        scrollBehavior = scrollBehavior,
        // The pager's header draws the one scrim over the bar, the search field and the chips together.
        barScrim = false,
    ) { padding ->
        BingeFilterChipPager(
            items = LogLevel.entries.map { FilterChipItem(label = stringResource(it.labelRes())) },
            selectedIndex = state.level.ordinal,
            onSelectedIndexChange = { actions.onLevelChange(LogLevel.entries[it]) },
            modifier = Modifier.fillMaxSize().padding(padding.outerPadding()),
            header = {
                // The bar's height joins the header, so the lines reach the top of the window and pass under both.
                Spacer(Modifier.height(padding.calculateTopPadding()))
                BingeSearchField(
                    query = state.search,
                    onQueryChange = actions.onSearchChange,
                    onClear = { actions.onSearchChange("") },
                    placeholder = stringResource(R.string.server_settings_logs_search),
                    modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
                )
            },
            headerBackground = Color.Transparent,
            scrimFraction = scrollBehavior.state.collapsedFraction,
        ) { pagePadding, page ->
            LogsPage(
                level = LogLevel.entries[page],
                state = state,
                entriesFor = entriesFor,
                events = events,
                actions = actions,
                contentPadding = PaddingValues(top = pagePadding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            )
        }
    }
}

/**
 * One level's page. The pager composes a page before the selection lands on it, while it is swiped
 * into view, so a page collects its own [level]'s lines and never the selected one's.
 *
 * Following is the selected page's alone, and so is the re-read it drives: a page merely swiped past
 * must not report its own scroll position as the one the interval follows.
 */
@Composable
private fun LogsPage(
    level: LogLevel,
    state: LogsUiState,
    entriesFor: (LogLevel) -> Flow<PagingData<LogEntry>>,
    events: Flow<LogsEvent>,
    actions: LogsActions,
    contentPadding: PaddingValues,
) {
    val lazyItems = entriesFor(level).collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val selected = level == state.level
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    LaunchedEffect(atTop, selected) { if (selected) actions.onFollowingChange(atTop) }
    LaunchedEffect(events, selected) {
        if (selected) {
            events.collect { event ->
                when (event) {
                    LogsEvent.Refresh -> lazyItems.refresh()
                }
            }
        }
    }
    LogsBody(lazyItems, listState, actions, Modifier.fillMaxSize(), contentPadding)
}

@Composable
private fun LogsBody(
    lazyItems: LazyPagingItems<LogEntry>,
    listState: LazyListState,
    actions: LogsActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val refresh = lazyItems.loadState.refresh
    when {
        lazyItems.itemCount > 0 ->
            LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = PaddingValues(dimensionResource(DesR.dimen.screen_content_inset)) + contentPadding,
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
            ) {
                items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
                    lazyItems[index]?.let { entry -> LogRow(entry, onCopy = { actions.onCopy(entry.copyText) }) }
                }
                item { PagedAppendState(lazyItems.loadState.append, onRetry = lazyItems::retry, onReconnect = actions.onBack) }
            }
        refresh is LoadState.Loading -> LoadingScreen(modifier.padding(contentPadding))
        refresh is LoadState.Error ->
            PagedRefreshError(
                refresh.error,
                onRetry = lazyItems::retry,
                onReconnect = actions.onBack,
                modifier = modifier.padding(contentPadding),
            )
        else ->
            EmptyScreen(
                message = stringResource(R.string.server_settings_logs_empty),
                modifier = modifier.padding(contentPadding),
                icon = Icons.Filled.Article,
            )
    }
}

/** One line: the level in its tone, the label and the time, the message, and the attached data once tapped open. */
@Composable
private fun LogRow(
    entry: LogEntry,
    onCopy: () -> Unit,
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = entry.data != null) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            Text(
                stringResource(entry.level.labelRes()).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = entry.level.sentiment().accent(),
            )
            Text(
                listOfNotNull(entry.label, formatRelativeOrAbsolute(entry.timestampMillis) ?: entry.timestampRaw.takeIf { it.isNotEmpty() })
                    .joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.server_settings_logs_copy))
            }
        }
        Text(entry.message, style = MaterialTheme.typography.bodyMedium)
        if (expanded) {
            entry.data?.let { data ->
                Text(
                    data,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LogLevel.labelRes(): Int =
    when (this) {
        LogLevel.Debug -> R.string.server_settings_log_debug
        LogLevel.Info -> R.string.server_settings_log_info
        LogLevel.Warn -> R.string.server_settings_log_warn
        LogLevel.Error -> R.string.server_settings_log_error
    }

private fun LogLevel.sentiment(): BingeSentiment =
    when (this) {
        LogLevel.Debug -> BingeSentiment.Neutral
        LogLevel.Info -> BingeSentiment.Info
        LogLevel.Warn -> BingeSentiment.Caution
        LogLevel.Error -> BingeSentiment.Negative
    }
