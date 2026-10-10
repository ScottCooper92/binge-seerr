package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.template.FilteredListScreen
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.SortSheet
import kotlinx.coroutines.flow.Flow

class IssuesActions(
    val onBack: () -> Unit,
    val onFilterChange: (IssueFilter) -> Unit,
    val onSortChange: (IssueSort) -> Unit,
    val onOpen: (IssueItem) -> Unit,
    val onRetryLoad: () -> Unit,
    /** A pull refreshed a list, so the chips re-read their counts. */
    val onRefreshCounts: () -> Unit,
)

/**
 * The issues browser: a page of cached, paged rows per filter, swiped between or picked from chips carrying
 * the server's totals. The ViewModel owns the selected filter; a chip's index is its filter's ordinal.
 *
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 * @param pullState a still frame's resting pull for every page; null gives each page M3's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(
    state: IssuesUiState,
    issuesFor: (IssueFilter) -> Flow<PagingData<IssueItem>>,
    actions: IssuesActions,
    showBack: Boolean = true,
    pullState: PullToRefreshState? = null,
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    val ready = state as? IssuesUiState.Ready
    FilteredListScreen(
        title = stringResource(R.string.hub_section_issues),
        onBack = actions.onBack.takeIf { showBack },
        filters =
            ready
                ?.let { loaded ->
                    IssueFilter.entries.map { FilterChipItem(label = stringResource(it.labelRes()), count = loaded.counts?.countFor(it)) }
                }.orEmpty(),
        selectedFilter = ready?.filter?.ordinal ?: 0,
        onFilterChange = { actions.onFilterChange(IssueFilter.entries[it]) },
        ready = ready != null,
        actions = {
            if (ready != null) {
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.requests_sort_cd))
                }
            }
        },
        notReady = { padding ->
            when (state) {
                is IssuesUiState.Error ->
                    ErrorScreen(error = state.error, modifier = Modifier.padding(padding), onRetry = actions.onRetryLoad)
                else -> LoadingScreen(Modifier.fillMaxSize().padding(padding))
            }
        },
    ) { page, contentPadding ->
        // Its own filter, never the selected one: the pager composes a page while it is swiped into view,
        // and keeps the pages either side of the selected one composed.
        val filter = IssueFilter.entries[page]
        ready?.let { loaded ->
            IssuesBody(
                filter = filter,
                lazyItems = issuesFor(filter).collectAsLazyPagingItems(),
                lastRefresh = loaded.refreshes[filter],
                onOpen = actions.onOpen,
                onPull = actions.onRefreshCounts,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                pullState = pullState,
            )
        }
    }
    if (showSort && ready != null) {
        SortSheet(
            choices = IssueSort.entries,
            selected = ready.sort,
            label = { stringResource(it.labelRes()) },
            onSelect = actions.onSortChange,
            onDismiss = { showSort = false },
        )
    }
}
