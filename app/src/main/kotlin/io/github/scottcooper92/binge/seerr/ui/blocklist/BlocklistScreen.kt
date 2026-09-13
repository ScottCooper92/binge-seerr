package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openTitle
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

class BlocklistActions(
    val onBack: () -> Unit,
    val onFilterChange: (BlocklistFilter) -> Unit,
    val onSearchChange: (String) -> Unit,
    val onRemove: (BlocklistItem) -> Unit,
)

/**
 * The blocklist browser: a search over the server's blocked titles, the source chips where the
 * server has them, and the paged rows. A title opens in Binge, or on the server; a manager unblocks from the
 * row, behind a confirm. A removal refreshes the pager in place, so the list keeps its position.
 */
@Composable
fun BlocklistScreen(
    state: BlocklistUiState,
    items: Flow<PagingData<BlocklistItem>>,
    events: Flow<BlocklistEvent>,
    actions: BlocklistActions,
) {
    val lazyItems = items.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val (message, kind) =
                when (event) {
                    BlocklistEvent.Removed -> {
                        lazyItems.refresh()
                        R.string.blocklist_removed to SnackbarMessageKind.Confirmation
                    }
                    is BlocklistEvent.CollectionChanged -> {
                        lazyItems.refresh()
                        val res = if (event.blocked) R.string.blocklist_collection_blocked else R.string.blocklist_collection_unblocked
                        res to SnackbarMessageKind.Confirmation
                    }
                    is BlocklistEvent.Failed -> event.error.messageRes() to SnackbarMessageKind.Error
                }
            snackbarHostState.showSnackbar(resources.getString(message), kind)
        }
    }
    val ready = state as? BlocklistUiState.Ready
    Scaffold(
        snackbarHost = { BingeSnackbarHost(snackbarHostState) },
        topBar = { BingeTopBar(title = stringResource(R.string.hub_section_blocklist), onBack = actions.onBack) },
    ) { padding ->
        if (ready == null) {
            LoadingScreen(Modifier.fillMaxSize().padding(padding))
        } else {
            BlocklistContent(ready, lazyItems, actions, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun BlocklistContent(
    state: BlocklistUiState.Ready,
    lazyItems: LazyPagingItems<BlocklistItem>,
    actions: BlocklistActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var removing by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(modifier) {
        BingeSearchField(
            query = state.search,
            onQueryChange = actions.onSearchChange,
            onClear = { actions.onSearchChange("") },
            placeholder = stringResource(R.string.blocklist_search_hint),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset))
                    .padding(top = dimensionResource(DesR.dimen.padding_s)),
        )
        if (state.hasFilters) {
            BingeFilterChipRow(
                items =
                    BlocklistFilter.entries.map {
                        FilterChipItem(
                            label = stringResource(it.labelRes()),
                            count = state.counts?.countFor(it),
                        )
                    },
                selectedIndex = state.filter.ordinal,
                onSelect = { actions.onFilterChange(BlocklistFilter.entries[it]) },
            )
        }
        BlocklistBody(
            lazyItems = lazyItems,
            isFiltered = state.isFiltered,
            actingTmdbIds = state.actingTmdbIds,
            canManage = state.canManage,
            onOpen = { item -> context.openTitle(item.mediaType, item.tmdbId, state.webRoot + item.mediaType.webPath() + item.tmdbId) },
            onRemove = { item -> removing = item.tmdbId },
            // A rejected session cannot be retried past: the hub owns reconnecting.
            onReconnect = actions.onBack,
            modifier = Modifier.fillMaxSize(),
        )
    }
    removing?.let { tmdbId ->
        val item = (0 until lazyItems.itemCount).asSequence().mapNotNull { lazyItems.peek(it) }.firstOrNull { it.tmdbId == tmdbId }
        if (item == null) {
            removing = null
        } else {
            BingeConfirmDialog(
                title = stringResource(R.string.blocklist_unblock_confirm_title, item.title ?: stringResource(R.string.blocklist_untitled)),
                message = stringResource(R.string.blocklist_unblock_confirm_message),
                confirmLabel = stringResource(R.string.blocklist_unblock),
                destructive = true,
                onConfirm = {
                    removing = null
                    actions.onRemove(item)
                },
                onDismiss = { removing = null },
            )
        }
    }
}

private fun RequestMediaType.webPath(): String = if (this == RequestMediaType.Tv) "tv/" else "movie/"
