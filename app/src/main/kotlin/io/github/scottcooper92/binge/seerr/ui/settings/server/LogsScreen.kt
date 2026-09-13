package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.accent
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.PagedRefreshError
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

class LogsActions(
    val onBack: () -> Unit,
    val onLevelChange: (LogLevel) -> Unit,
    val onSearchChange: (String) -> Unit,
    val onFollowingChange: (Boolean) -> Unit,
    val onCopy: (String) -> Unit,
)

/** The logs page: a level and a search over the paged log, each line expandable to what it carried, and a copy. */
@Composable
fun LogsScreen(
    state: LogsUiState,
    entries: Flow<PagingData<LogEntry>>,
    refreshTicks: Flow<Unit>,
    actions: LogsActions,
) {
    val lazyItems = entries.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    LaunchedEffect(atTop) { actions.onFollowingChange(atTop) }
    LaunchedEffect(refreshTicks) { refreshTicks.collect { lazyItems.refresh() } }
    Scaffold(topBar = { BingeTopBar(title = stringResource(R.string.server_settings_logs), onBack = actions.onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BingeSearchField(
                query = state.search,
                onQueryChange = actions.onSearchChange,
                onClear = { actions.onSearchChange("") },
                placeholder = stringResource(R.string.server_settings_logs_search),
                modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
            )
            BingeFilterChipRow(
                items = LogLevel.entries.map { FilterChipItem(label = stringResource(it.labelRes())) },
                selectedIndex = state.level.ordinal,
                onSelect = { actions.onLevelChange(LogLevel.entries[it]) },
            )
            LogsBody(lazyItems, listState, actions, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun LogsBody(
    lazyItems: LazyPagingItems<LogEntry>,
    listState: LazyListState,
    actions: LogsActions,
    modifier: Modifier = Modifier,
) {
    val refresh = lazyItems.loadState.refresh
    when {
        lazyItems.itemCount > 0 ->
            LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = PaddingValues(dimensionResource(DesR.dimen.screen_content_inset)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
            ) {
                items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
                    lazyItems[index]?.let { entry -> LogRow(entry, onCopy = { actions.onCopy(entry.copyText) }) }
                }
                item { PagedAppendState(lazyItems.loadState.append, onRetry = lazyItems::retry, onReconnect = actions.onBack) }
            }
        refresh is LoadState.Loading -> LoadingScreen(modifier)
        refresh is LoadState.Error ->
            PagedRefreshError(
                refresh.error,
                onRetry = lazyItems::retry,
                onReconnect = actions.onBack,
                modifier = modifier,
            )
        else -> EmptyScreen(message = stringResource(R.string.server_settings_logs_empty), modifier = modifier, icon = Icons.Filled.Article)
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
