package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.OverlaidHeaderContent
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.rememberFilterPagerState
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openTitle
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
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
 * The blocklist browser: a search over the server's blocked titles, a page of paged rows per source
 * chip where the server has them, and one list where it does not. A title opens in Binge, or on the
 * server; a manager unblocks from the row, behind a confirm.
 *
 * The overlay is assembled here rather than taken from `BingeFilterChipPager`, because the search
 * field has to stay composed — and keep its focus and its text — whether or not the chips are there.
 *
 * @param itemsFor one cached paged list per filter, so a page swiped away and back keeps its rows.
 * @param shouldRefresh true at most once per list version per filter, so a freshly composed, current
 * page does not blank-refresh after a removal elsewhere.
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(
    state: BlocklistUiState,
    itemsFor: (BlocklistFilter) -> Flow<PagingData<BlocklistItem>>,
    events: Flow<BlocklistEvent>,
    shouldRefresh: (BlocklistFilter, Int) -> Boolean,
    actions: BlocklistActions,
    showBack: Boolean = true,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val (message, kind) =
                when (event) {
                    BlocklistEvent.Removed -> R.string.blocklist_removed to SnackbarMessageKind.Confirmation
                    is BlocklistEvent.CollectionChanged -> {
                        val res = if (event.blocked) R.string.blocklist_collection_blocked else R.string.blocklist_collection_unblocked
                        res to SnackbarMessageKind.Confirmation
                    }
                    is BlocklistEvent.Failed -> event.error.messageRes() to SnackbarMessageKind.Error
                }
            snackbarHostState.showSnackbar(resources.getString(message), kind)
        }
    }
    val ready = state as? BlocklistUiState.Ready
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ScreenScaffold(
        title = stringResource(R.string.hub_section_blocklist),
        onBack = actions.onBack.takeIf { showBack },
        snackbarHostState = snackbarHostState,
        scrollBehavior = scrollBehavior,
        // The overlay below draws the one scrim over the bar, the search field and the chips together.
        barScrim = ready == null,
    ) { padding ->
        if (ready == null) {
            LoadingScreen(Modifier.fillMaxSize().padding(padding))
        } else {
            BlocklistPages(
                state = ready,
                itemsFor = itemsFor,
                shouldRefresh = shouldRefresh,
                actions = actions,
                barHeight = padding.calculateTopPadding(),
                bottomPadding = padding.calculateBottomPadding(),
                scrimFraction = scrollBehavior.state.collapsedFraction,
                modifier = Modifier.fillMaxSize().padding(padding.outerPadding()),
            )
        }
    }
}

/** The search field and the chips over a page per filter — or, on a server without them, one page. */
@Composable
private fun BlocklistPages(
    state: BlocklistUiState.Ready,
    itemsFor: (BlocklistFilter) -> Flow<PagingData<BlocklistItem>>,
    shouldRefresh: (BlocklistFilter, Int) -> Boolean,
    actions: BlocklistActions,
    barHeight: Dp,
    bottomPadding: Dp,
    scrimFraction: Float,
    modifier: Modifier = Modifier,
) {
    val filters = if (state.hasFilters) BlocklistFilter.entries else listOf(BlocklistFilter.All)
    val pagerState =
        rememberFilterPagerState(
            selectedIndex = filters.indexOf(state.filter).coerceAtLeast(0),
            onSelectedIndexChange = { actions.onFilterChange(filters[it]) },
            pageCount = filters.size,
        )
    OverlaidHeaderContent(
        modifier = modifier,
        headerBackground = Color.Transparent,
        scrimFraction = scrimFraction,
        header = {
            // The bar's height joins the header, so the rows reach the top of the window and pass under both.
            Spacer(Modifier.height(barHeight))
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
                        filters.map {
                            FilterChipItem(label = stringResource(it.labelRes()), count = state.counts?.countFor(it))
                        },
                    selectedIndex = pagerState.currentPage,
                    onSelect = { actions.onFilterChange(filters[it]) },
                )
            }
        },
    ) { overlay ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            BlocklistPage(
                filter = filters[page],
                state = state,
                itemsFor = itemsFor,
                shouldRefresh = shouldRefresh,
                actions = actions,
                contentPadding = PaddingValues(top = overlay.calculateTopPadding(), bottom = bottomPadding),
            )
        }
    }
}

/**
 * One filter's page. The pager composes a page before the selection lands on it, while it is swiped
 * into view, so a page collects its own [filter]'s rows and never the selected one's. The confirm
 * dialog belongs to the selected page alone: a title can sit under two filters, and two composed
 * pages would otherwise raise two dialogs for it.
 */
@Composable
private fun BlocklistPage(
    filter: BlocklistFilter,
    state: BlocklistUiState.Ready,
    itemsFor: (BlocklistFilter) -> Flow<PagingData<BlocklistItem>>,
    shouldRefresh: (BlocklistFilter, Int) -> Boolean,
    actions: BlocklistActions,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val lazyItems = itemsFor(filter).collectAsLazyPagingItems()
    val selected = filter == state.filter
    var removing by rememberSaveable { mutableStateOf<Int?>(null) }
    // A removal bumps the version; a stale page refreshes once it is selected, anchored, keeping its scroll.
    LaunchedEffect(state.listVersion, selected) {
        if (selected && shouldRefresh(filter, state.listVersion)) lazyItems.refresh()
    }
    BlocklistBody(
        lazyItems = lazyItems,
        isFiltered = state.isFiltered(filter),
        actingTmdbIds = state.actingTmdbIds,
        canManage = state.canManage,
        onOpen = { item -> context.openTitle(item.mediaType, item.tmdbId, state.webRoot + item.mediaType.webPath() + item.tmdbId) },
        onRemove = { item -> removing = item.tmdbId },
        // A rejected session cannot be retried past: the hub owns reconnecting.
        onReconnect = actions.onBack,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    )
    if (selected) {
        removing?.let { tmdbId ->
            val item = (0 until lazyItems.itemCount).asSequence().mapNotNull { lazyItems.peek(it) }.firstOrNull { it.tmdbId == tmdbId }
            if (item == null) {
                removing = null
            } else {
                BingeConfirmDialog(
                    title =
                        stringResource(
                            R.string.blocklist_unblock_confirm_title,
                            item.title ?: stringResource(R.string.blocklist_untitled),
                        ),
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
}

private fun RequestMediaType.webPath(): String = if (this == RequestMediaType.Tv) "tv/" else "movie/"
