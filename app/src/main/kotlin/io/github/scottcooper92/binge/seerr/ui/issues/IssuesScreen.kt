package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.FilterChipItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.SortSheet
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import kotlinx.coroutines.flow.Flow

class IssuesActions(
    val onBack: () -> Unit,
    val onFilterChange: (IssueFilter) -> Unit,
    val onSortChange: (IssueSort) -> Unit,
    val onOpen: (IssueItem) -> Unit,
)

/**
 * The issues browser: filter chips with the server's totals over the selected filter's cached, paged rows.
 *
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 */
@Composable
fun IssuesScreen(
    state: IssuesUiState,
    issuesFor: (IssueFilter) -> Flow<PagingData<IssueItem>>,
    actions: IssuesActions,
    showBack: Boolean = true,
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    val ready = state as? IssuesUiState.Ready
    val header: (@Composable () -> Unit)? =
        ready?.let { chips ->
            @Composable {
                BingeFilterChipRow(
                    items =
                        IssueFilter.entries.map {
                            FilterChipItem(label = stringResource(it.labelRes()), count = chips.counts?.countFor(it))
                        },
                    selectedIndex = chips.filter.ordinal,
                    onSelect = { actions.onFilterChange(IssueFilter.entries[it]) },
                )
            }
        }
    ScreenScaffold(
        title = stringResource(R.string.hub_section_issues),
        onBack = actions.onBack.takeIf { showBack },
        header = header,
        actions = {
            if (ready != null) {
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.requests_sort_cd))
                }
            }
        },
    ) { padding ->
        if (ready == null) {
            LoadingScreen(Modifier.fillMaxSize().padding(padding))
        } else {
            Box(Modifier.fillMaxSize().padding(padding.outerPadding())) {
                IssuesBody(
                    filter = ready.filter,
                    lazyItems = issuesFor(ready.filter).collectAsLazyPagingItems(),
                    onOpen = actions.onOpen,
                    // A rejected session cannot be retried past: the hub owns reconnecting.
                    onReconnect = actions.onBack,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding.innerPadding(),
                )
            }
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
