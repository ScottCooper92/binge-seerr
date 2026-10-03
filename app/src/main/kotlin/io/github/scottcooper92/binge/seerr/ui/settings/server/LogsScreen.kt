package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeFilterChipPager
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.accent
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.PagedRefreshError
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import io.github.scottcooper92.binge.seerr.ui.state.resolvedListContentPadding
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
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ScreenScaffold(
        title = stringResource(R.string.server_settings_logs),
        onBack = actions.onBack,
        scrollBehavior = scrollBehavior,
        // The pager's header draws the one opaque background over the bar, the search field and the chips together.
        barScrim = false,
    ) { padding ->
        BingeFilterChipPager(
            items = LogLevel.entries.map { FilterChipItem(label = stringResource(it.labelRes())) },
            selectedIndex = state.level.ordinal,
            onSelectedIndexChange = { actions.onLevelChange(LogLevel.entries[it]) },
            modifier = Modifier.fillMaxSize().padding(padding.outerPadding()),
            header = {
                // The bar's height joins the header, so the lines reach the top of the window and pass under both.
                // Opaque header (the default), not transparent-with-a-scrim, so scrolled rows never show
                // through underneath it once it's pinned at the top.
                Spacer(Modifier.height(padding.calculateTopPadding()))
                BingeSearchField(
                    query = state.search,
                    onQueryChange = actions.onSearchChange,
                    onClear = { actions.onSearchChange("") },
                    placeholder = stringResource(R.string.server_settings_logs_search),
                    // The list filters as it is typed, so Search has nothing left to run: it puts the keyboard away.
                    onSubmit = { focusManager.clearFocus() },
                    modifier = Modifier.padding(horizontal = resolvedContentInset()),
                )
            },
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
                contentPadding = resolvedListContentPadding(contentPadding),
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
                icon = Icons.AutoMirrored.Filled.Article,
            )
    }
}

private const val COLLAPSED_MESSAGE_LINES = 3

/**
 * One line as a card: a stripe, an icon and the level's name all carry the severity, so it never
 * rests on colour alone. The message is held to a few lines until the row is opened; a row with
 * attached data or a clipped message shows a chevron, and a long press copies the line either way.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LogRow(
    entry: LogEntry,
    onCopy: () -> Unit,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(initiallyExpanded) }
    var clipped by remember { mutableStateOf(false) }
    val expandable = entry.data != null || clipped || expanded
    val tone = entry.level.sentiment().accent()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BingeShapes.Medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .height(IntrinsicSize.Min)
                .combinedClickable(
                    onClickLabel =
                        if (expandable) {
                            stringResource(if (expanded) R.string.server_settings_logs_collapse else R.string.server_settings_logs_expand)
                        } else {
                            null
                        },
                    onClick = { if (expandable) expanded = !expanded },
                    onLongClick = onCopy,
                    onLongClickLabel = stringResource(R.string.server_settings_logs_copy),
                ),
    ) {
        Box(Modifier.width(dimensionResource(R.dimen.log_row_severity_stripe_width)).fillMaxHeight().background(tone))
        Column(
            modifier = Modifier.weight(1f).padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            LogRowHeader(entry, tone, expandable, expanded)
            Text(
                entry.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_MESSAGE_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) clipped = it.hasVisualOverflow },
            )
            if (expanded) LogRowDetails(entry, onCopy)
        }
    }
}

@Composable
private fun LogRowHeader(
    entry: LogEntry,
    tone: Color,
    expandable: Boolean,
    expanded: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        Icon(
            entry.level.icon(),
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(dimensionResource(R.dimen.log_row_level_icon_size)),
        )
        Text(stringResource(entry.level.labelRes()).uppercase(), style = MaterialTheme.typography.labelMedium, color = tone)
        val meta =
            listOfNotNull(
                entry.label,
                formatRelativeOrAbsolute(entry.timestampMillis) ?: entry.timestampRaw.takeIf { it.isNotEmpty() },
            )
        if (meta.isNotEmpty()) {
            val separator = stringResource(R.string.hub_meta_separator)
            Text(
                meta.joinToString(separator, prefix = separator),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (expandable) {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription =
                    stringResource(if (expanded) R.string.server_settings_logs_collapse else R.string.server_settings_logs_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.LogRowDetails(
    entry: LogEntry,
    onCopy: () -> Unit,
) {
    entry.data?.let { data ->
        Text(
            data,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BingeShapes.ElementSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(dimensionResource(DesR.dimen.padding_s)),
        )
    }
    TextButton(onClick = onCopy, modifier = Modifier.align(Alignment.End)) {
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(dimensionResource(R.dimen.log_row_level_icon_size)),
        )
        Spacer(Modifier.width(dimensionResource(DesR.dimen.padding_xs)))
        Text(stringResource(R.string.server_settings_logs_copy))
    }
}

private fun LogLevel.icon(): ImageVector =
    when (this) {
        LogLevel.Debug -> Icons.Filled.BugReport
        LogLevel.Info -> Icons.Filled.Info
        LogLevel.Warn -> Icons.Filled.Warning
        LogLevel.Error -> Icons.Filled.Error
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
