package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.LogRowEntry
import com.binge.designsystem.component.LogRowLevel
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.template.FilteredListScreen
import com.binge.designsystem.template.PagedPhase
import com.binge.designsystem.template.ScreenBar
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.PagedAppendState
import com.cooper.binge.companion.seerr.ui.requests.PagedRefreshError
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.filteredPagePadding
import com.cooper.binge.companion.seerr.ui.state.refreshInFlight
import com.cooper.binge.companion.seerr.ui.state.rememberPagedPhase
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR
import com.binge.designsystem.component.LogRow as DesLogRow

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
    pullState: PullToRefreshState? = null,
) {
    val focusManager = LocalFocusManager.current
    FilteredListScreen(
        title = stringResource(R.string.server_settings_logs),
        onBack = actions.onBack,
        // The console keeps its single-line bar and an opaque header; the default is the gallery's transparent collapsing bar.
        bar = ScreenBar.Small,
        headerBackground = MaterialTheme.colorScheme.background,
        scrimFraction = 0f,
        filters = LogLevel.entries.map { FilterChipItem(label = stringResource(it.labelRes())) },
        selectedFilter = state.level.ordinal,
        onFilterChange = { actions.onLevelChange(LogLevel.entries[it]) },
        // As before: only the selected level's lines are read, not the levels either side of it.
        beyondViewportPageCount = 0,
        search = {
            BingeSearchField(
                query = state.search,
                onQueryChange = actions.onSearchChange,
                onClear = { actions.onSearchChange("") },
                placeholder = stringResource(R.string.server_settings_logs_search),
                // The list filters as it is typed, so Search has nothing left to run: it puts the keyboard away.
                onSubmit = { focusManager.clearFocus() },
                modifier = Modifier.padding(resolvedContentPadding()),
            )
        },
    ) { page, contentPadding ->
        LogsPage(
            level = LogLevel.entries[page],
            state = state,
            entriesFor = entriesFor,
            events = events,
            actions = actions,
            contentPadding = contentPadding,
            pullState = pullState,
        )
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
    pullState: PullToRefreshState?,
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
    // Only a pull shows the spinner: following re-reads this list on its own interval, and a spinner on each would blink.
    var pulled by remember { mutableStateOf(false) }
    val refreshing = lazyItems.loadState.refreshInFlight()
    LaunchedEffect(refreshing) { if (!refreshing) pulled = false }
    BingePullToRefresh(
        isRefreshing = pulled && refreshing,
        onRefresh = {
            if (!refreshing) {
                pulled = true
                lazyItems.refresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
        indicatorTopInset = contentPadding.calculateTopPadding(),
        state = pullState ?: rememberPullToRefreshState(),
    ) {
        LogsBody(lazyItems, listState, actions, Modifier.fillMaxSize(), contentPadding)
    }
}

@Composable
private fun LogsBody(
    lazyItems: LazyPagingItems<LogEntry>,
    listState: LazyListState,
    actions: LogsActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // Read straight from a paging source, so there is no network refresh to wait on.
    when (val phase = lazyItems.rememberPagedPhase(lastRefresh = null)) {
        is PagedPhase.Rows ->
            LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = contentPadding.filteredPagePadding(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
            ) {
                items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
                    lazyItems[index]?.let { entry -> LogRow(entry, onCopy = { actions.onCopy(entry.copyText) }) }
                }
                item { PagedAppendState(lazyItems.loadState.append, onRetry = lazyItems::retry) }
            }
        PagedPhase.Skeleton -> LoadingScreen(modifier.padding(contentPadding))
        is PagedPhase.Failed ->
            PagedRefreshError(
                phase.error,
                onRetry = lazyItems::retry,
                modifier = modifier.padding(contentPadding),
            )
        PagedPhase.Empty ->
            EmptyScreen(
                message = stringResource(R.string.server_settings_logs_empty),
                modifier = modifier.padding(contentPadding),
                icon = Icons.AutoMirrored.Filled.Article,
            )
    }
}

/** One line as the design system's [DesLogRow]: [LogEntry] is mapped to its model here, and a long press calls [onCopy]. */
@Composable
internal fun LogRow(
    entry: LogEntry,
    onCopy: () -> Unit,
    initiallyExpanded: Boolean = false,
) = DesLogRow(entry.toRowEntry(), onCopy = onCopy, initiallyExpanded = initiallyExpanded)

private fun LogEntry.toRowEntry() =
    LogRowEntry(
        id = id,
        timestampMillis = timestampMillis,
        timestampRaw = timestampRaw,
        level = level.toRowLevel(),
        label = label,
        message = message,
        data = data,
    )

private fun LogLevel.toRowLevel(): LogRowLevel =
    when (this) {
        LogLevel.Debug -> LogRowLevel.Debug
        LogLevel.Info -> LogRowLevel.Info
        LogLevel.Warn -> LogRowLevel.Warn
        LogLevel.Error -> LogRowLevel.Error
    }

private fun LogLevel.labelRes(): Int =
    when (this) {
        LogLevel.Debug -> R.string.server_settings_log_debug
        LogLevel.Info -> R.string.server_settings_log_info
        LogLevel.Warn -> R.string.server_settings_log_warn
        LogLevel.Error -> R.string.server_settings_log_error
    }
