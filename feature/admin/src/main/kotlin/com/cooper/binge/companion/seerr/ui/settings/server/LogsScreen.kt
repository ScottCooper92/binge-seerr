package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.paneBackOrNull
import com.binge.designsystem.template.LogReaderCallbacks
import com.binge.designsystem.template.LogReaderScreen
import com.binge.designsystem.template.LogReaderState
import com.cooper.binge.companion.seerr.feature.admin.R
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.state.refreshInFlight
import com.cooper.binge.companion.seerr.ui.state.toErrorKind
import com.cooper.homelab.core.presentation.diagnostics.ServerLogEntry
import com.cooper.homelab.core.presentation.diagnostics.ServerLogLevel
import com.cooper.homelab.core.uiandroid.diagnostics.toLogRow
import kotlinx.coroutines.flow.Flow

class LogsActions(
    val onBack: () -> Unit,
    val onLevelChange: (ServerLogLevel) -> Unit,
    val onSearchChange: (String) -> Unit,
    val onFollowingChange: (Boolean) -> Unit,
    val onCopy: (String) -> Unit,
)

/**
 * The logs page: the design system's log reader over the selected level's paged lines and a search that spans every level. Each line
 * expands to what it carried, and copies. A pull re-reads the list, and while the list is at its top it is re-read on an interval, so
 * the newest lines arrive on their own.
 *
 * @param entriesFor one cached paged list per level, so a level picked again keeps its lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    state: LogsUiState,
    entriesFor: (ServerLogLevel) -> Flow<PagingData<ServerLogEntry>>,
    events: Flow<LogsEvent>,
    actions: LogsActions,
    pullState: PullToRefreshState? = null,
) {
    // One flow per level for as long as the level holds: a flow built afresh on each recomposition would restart the paging each time.
    val entries = remember(state.level) { entriesFor(state.level) }
    val lazyItems = entries.collectAsLazyPagingItems()
    LaunchedEffect(events, lazyItems) {
        events.collect { event ->
            when (event) {
                LogsEvent.Refresh -> lazyItems.refresh()
            }
        }
    }
    // Only a pull shows the spinner: following re-reads this list on its own interval, and a spinner on each would blink.
    var pulled by remember(lazyItems) { mutableStateOf(false) }
    val refreshing = lazyItems.loadState.refreshInFlight()
    LaunchedEffect(refreshing) { if (!refreshing) pulled = false }
    val levels = ServerLogLevel.entries
    val chips = levels.map { FilterChipItem(label = stringResource(it.labelRes())) }
    val emptyMessage = stringResource(R.string.server_settings_logs_empty)
    val readerState = lazyItems.toReaderState(refreshing = pulled && refreshing, emptyMessage = emptyMessage)
    LogReaderScreen(
        title = stringResource(R.string.server_settings_logs),
        levels = chips,
        selected = state.level.ordinal,
        query = state.search,
        state = readerState,
        callbacks =
            LogReaderCallbacks(
                onSelectLevel = { actions.onLevelChange(levels[it]) },
                onQueryChange = actions.onSearchChange,
                // Reading the last row is what asks a paged list for the next page.
                onLoadMore = { if (lazyItems.itemCount > 0) lazyItems[lazyItems.itemCount - 1] },
                onRetry = lazyItems::retry,
                onCopy = { row ->
                    lazyItems.itemSnapshotList.items
                        .firstOrNull { it.id == row.id }
                        ?.let { actions.onCopy(it.copyText) }
                },
                onBack = paneBackOrNull(actions.onBack),
                onRefresh = {
                    if (!refreshing) {
                        pulled = true
                        lazyItems.refresh()
                    }
                },
                onAtTopChange = actions.onFollowingChange,
            ),
        searchPlaceholder = stringResource(R.string.server_settings_logs_search),
        pullState = pullState,
    )
}

/**
 * The reader's body for this list. Rows already on screen stay while a refresh runs or fails behind them, so following never swaps the
 * lines for a skeleton; a failure shows only when there is nothing to show.
 */
@Composable
private fun LazyPagingItems<ServerLogEntry>.toReaderState(
    refreshing: Boolean,
    emptyMessage: String,
): LogReaderState {
    val refresh = loadState.refresh
    val rows = remember(itemSnapshotList) { itemSnapshotList.items.map { it.toLogRow() } }
    return when {
        itemCount == 0 && refresh is LoadState.Error -> LogReaderState.Failed(refresh.error.toSeerrError().toErrorKind())
        itemCount == 0 && refresh is LoadState.Loading -> LogReaderState.Loading
        itemCount == 0 -> LogReaderState.Empty(message = emptyMessage, icon = Icons.AutoMirrored.Filled.Article)
        else ->
            LogReaderState.Ready(
                rows = rows,
                hasMore = !loadState.append.endOfPaginationReached,
                appending = loadState.append is LoadState.Loading,
                appendFailed = loadState.append is LoadState.Error,
                refreshing = refreshing,
            )
    }
}

private fun ServerLogLevel.labelRes(): Int =
    when (this) {
        ServerLogLevel.Debug -> R.string.server_settings_log_debug
        ServerLogLevel.Info -> R.string.server_settings_log_info
        ServerLogLevel.Warn -> R.string.server_settings_log_warn
        ServerLogLevel.Error -> R.string.server_settings_log_error
    }
