package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.BingeFilterChipPager
import com.binge.designsystem.component.FilterChipItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.SortSheet
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import kotlinx.coroutines.flow.Flow

class IssuesActions(
    val onBack: () -> Unit,
    val onFilterChange: (IssueFilter) -> Unit,
    val onSortChange: (IssueSort) -> Unit,
    val onOpen: (IssueItem) -> Unit,
)

/**
 * The issues browser: a page of cached, paged rows per filter, swiped between or picked from chips carrying
 * the server's totals. The ViewModel owns the selected filter; a chip's index is its filter's ordinal.
 *
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(
    state: IssuesUiState,
    issuesFor: (IssueFilter) -> Flow<PagingData<IssueItem>>,
    actions: IssuesActions,
    showBack: Boolean = true,
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    val ready = state as? IssuesUiState.Ready
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ScreenScaffold(
        title = stringResource(R.string.hub_section_issues),
        onBack = actions.onBack.takeIf { showBack },
        scrollBehavior = scrollBehavior,
        // The pager's header draws the one scrim over the bar and the chips together.
        barScrim = ready == null,
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
            BingeFilterChipPager(
                items =
                    IssueFilter.entries.map {
                        FilterChipItem(label = stringResource(it.labelRes()), count = ready.counts?.countFor(it))
                    },
                selectedIndex = ready.filter.ordinal,
                onSelectedIndexChange = { actions.onFilterChange(IssueFilter.entries[it]) },
                modifier = Modifier.fillMaxSize().padding(padding.outerPadding()),
                // The bar's height joins the pager's header, so the rows reach the top of the window and pass under both.
                // Opaque header (the default), not transparent-with-a-scrim, so scrolled rows never show
                // through underneath it once it's pinned at the top.
                header = { Spacer(Modifier.height(padding.calculateTopPadding())) },
            ) { pagePadding, page ->
                // Its own filter, never the selected one: the pager composes a page while it is swiped into view.
                val filter = IssueFilter.entries[page]
                IssuesBody(
                    filter = filter,
                    lazyItems = issuesFor(filter).collectAsLazyPagingItems(),
                    onOpen = actions.onOpen,
                    // A rejected session cannot be retried past: the hub owns reconnecting.
                    onReconnect = actions.onBack,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = pagePadding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
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
