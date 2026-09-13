package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.FilterChipItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import kotlinx.coroutines.flow.Flow

class RequestsActions(
    val onBack: () -> Unit,
    val onFilterChange: (RequestFilter) -> Unit,
    val onSortChange: (RequestSort) -> Unit,
    val onOpen: (RequestItem) -> Unit,
)

/** The requests browser: filter chips with the server's totals over the selected filter's paged rows. */
@Composable
fun RequestsScreen(
    state: RequestsUiState,
    requestsFor: (RequestFilter) -> Flow<PagingData<RequestItem>>,
    actions: RequestsActions,
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    val ready = state as? RequestsUiState.Ready
    Scaffold(
        topBar = {
            BingeTopBar(
                title = stringResource(R.string.hub_section_requests),
                onBack = actions.onBack,
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.requests_sort_cd))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (ready == null) {
            LoadingScreen(Modifier.fillMaxSize().padding(padding))
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                BingeFilterChipRow(
                    items =
                        RequestFilter.entries.map {
                            FilterChipItem(
                                label = stringResource(it.labelRes()),
                                count = ready.counts?.countFor(it),
                            )
                        },
                    selectedIndex = ready.filter.ordinal,
                    onSelect = { actions.onFilterChange(RequestFilter.entries[it]) },
                )
                val lazyItems = requestsFor(ready.filter).collectAsLazyPagingItems()
                RequestsBody(
                    filter = ready.filter,
                    lazyItems = lazyItems,
                    onOpen = actions.onOpen,
                    // A rejected session cannot be retried past: the hub owns reconnecting.
                    onReconnect = actions.onBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    if (showSort && ready != null) {
        RequestSortSheet(selected = ready.sort, onSelect = actions.onSortChange, onDismiss = { showSort = false })
    }
}
