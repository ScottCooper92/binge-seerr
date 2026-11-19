package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.BackdropTile
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.CarouselSkeleton
import com.binge.designsystem.component.DarkStatusBarEffect
import com.binge.designsystem.component.DiscoverEntryRow
import com.binge.designsystem.component.HeroCarousel
import com.binge.designsystem.component.LogoTile
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.component.StatusBarScrim
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.modifier.skeleton
import com.binge.designsystem.navOverlayEnd
import com.binge.designsystem.navOverlayPadding
import com.binge.designsystem.navOverlayStart
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.HubTile
import com.cooper.binge.companion.seerr.discover.sliders.seeAll
import com.cooper.binge.companion.seerr.discover.sliders.tileGrid
import com.cooper.binge.companion.seerr.discover.sliders.titleKind
import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.binge.designsystem.R as DesR

private const val SKELETON_ROWS = 3
private const val BLANK = " "
private const val HERO_KEY = "hero"
private const val BROWSE_ALL_KEY = "browse_all"

/**
 * One discovery hub, Movies or TV: the server's rows of posters, each loading on its own. [onOpenTitle] gets the card the user
 * tapped. The hub has no title bar. The hero runs edge to edge under the status bar, whose icons go light only over a real hero.
 * Its place is always there on a loaded hub, as Binge keeps it: an empty carousel while it loads and after it fails or comes back
 * empty, so the rows never move when it answers. The hub's name is its pane title, for a screen reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverHubScreen(
    tab: HubTab,
    state: DiscoverHubUiState,
    onRefresh: () -> Unit,
    onOpenTitle: (HubCard) -> Unit,
    onOpenGrid: (HubGrid, String) -> Unit,
    onOpenRequests: () -> Unit,
) {
    val title = stringResource(if (tab == HubTab.Movies) R.string.hubs_title_movies else R.string.hubs_title_tv)
    val hasHero = (state as? DiscoverHubUiState.Ready)?.hero?.isNotEmpty() == true
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val reservesHero = state is DiscoverHubUiState.Loading || (state as? DiscoverHubUiState.Ready)?.heroPlace == true
    val inner = navOverlayPadding(if (reservesHero) PaddingValues() else PaddingValues(top = statusBar))
    // The scrolling states leave Binge's 16dp below their last row, and run under the rail, padding their own first column.
    val scrolling =
        navOverlayPadding(
            if (reservesHero) {
                PaddingValues(
                    bottom = dimensionResource(DesR.dimen.padding_m),
                )
            } else {
                PaddingValues(top = statusBar, bottom = dimensionResource(DesR.dimen.padding_m))
            },
            bleedStart = true,
        )
    Box(modifier = Modifier.fillMaxSize().semantics { paneTitle = title }) {
        when (state) {
            DiscoverHubUiState.Loading -> HubSkeleton(scrolling)
            DiscoverHubUiState.Empty -> EmptyScreen(stringResource(R.string.hubs_empty_message), Modifier.padding(inner))
            is DiscoverHubUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry = onRefresh)
            is DiscoverHubUiState.Ready ->
                BingePullToRefresh(isRefreshing = state.refreshing, onRefresh = onRefresh, indicatorTopInset = statusBar) {
                    HubRows(tab, state, scrolling, onOpenTitle, onOpenGrid, onOpenRequests)
                }
        }
        if (hasHero) {
            DarkStatusBarEffect()
            StatusBarScrim()
        }
    }
}

@Composable
private fun HubSkeleton(contentPadding: PaddingValues) {
    LazyColumn(contentPadding = contentPadding) {
        item {
            HeroCarousel(
                items = emptyList(),
                onItemClick = {},
                modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Hub.HERO)),
            )
        }
        item { BrowseAllSkeleton() }
        items(SKELETON_ROWS) { index ->
            CarouselSkeleton(modifier = Modifier.firstCarouselAnchor(index), description = stringResource(R.string.hubs_loading_row))
        }
    }
}

/**
 * The Browse all row the loaded hub puts above its rails, drawn as the row itself with blank text under the skeleton plate, so its
 * height follows the font as the real row's does and the first rail lands where its plate stood.
 */
@Composable
private fun BrowseAllSkeleton() {
    Box(Modifier.padding(start = resolvedContentInset() + navOverlayStart(), end = resolvedContentInset() + navOverlayEnd())) {
        DiscoverEntryRow(
            title = BLANK,
            subtitle = BLANK,
            onClick = {},
            modifier =
                Modifier
                    .layoutAnchor(LayoutAnchors.section(LayoutAnchors.Hub.DISCOVER_ENTRY))
                    .skeleton(visible = true, shape = MaterialTheme.shapes.large)
                    .clearAndSetSemantics { },
            startPadding = Dp.Hairline,
            endPadding = Dp.Hairline,
        )
    }
}

/** Marks the leading carousel, skeleton or loaded, so the geometry test can check the first row lands where its plate stood. */
private fun Modifier.firstCarouselAnchor(index: Int): Modifier =
    if (index == 0) layoutAnchor(LayoutAnchors.section(LayoutAnchors.Hub.FIRST_CAROUSEL)) else this

@Composable
private fun HubRows(
    tab: HubTab,
    state: DiscoverHubUiState.Ready,
    contentPadding: PaddingValues,
    onOpenTitle: (HubCard) -> Unit,
    onOpenGrid: (HubGrid, String) -> Unit,
    onOpenRequests: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (state.heroPlace) item(key = HERO_KEY) { Hero(state.hero, onOpenTitle) }
        if (state.browseAll) item(key = BROWSE_ALL_KEY) { BrowseAllRow(tab, onOpenGrid) }
        itemsIndexed(state.rows, key = { _, row -> row.row.sliderId }) { index, row ->
            val anchor = Modifier.firstCarouselAnchor(index)
            when (row) {
                is HubRowUi.Cards -> CardRow(tab, row, onOpenTitle, onOpenGrid, onOpenRequests, anchor)
                is HubRowUi.Tiles -> TileRow(tab, row, onOpenGrid, anchor)
            }
        }
        // A row still loading is a skeleton below the ones that landed, so the page does not jump when it arrives.
        items(state.pending) { CarouselSkeleton(description = stringResource(R.string.hubs_loading_row)) }
    }
}

/** The way into the unfiltered grid, under the hero as Binge puts it: the popular list, with the grid's filters. */
@Composable
private fun BrowseAllRow(
    tab: HubTab,
    onOpenGrid: (HubGrid, String) -> Unit,
) {
    val movies = tab == HubTab.Movies
    val grid = HubGrid(tab.titleKind(), GridPreset.Popular)
    val gridTitle = stringResource(if (movies) R.string.hubs_row_popular_movies else R.string.hubs_row_popular_tv)
    DiscoverEntryRow(
        title = stringResource(if (movies) R.string.hubs_browse_all_movies else R.string.hubs_browse_all_tv),
        subtitle = stringResource(if (movies) R.string.hubs_browse_subtitle_movies else R.string.hubs_browse_subtitle_tv),
        onClick = { onOpenGrid(grid, gridTitle) },
        modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Hub.DISCOVER_ENTRY)),
    )
}

@Composable
private fun CardRow(
    tab: HubTab,
    row: HubRowUi.Cards,
    onOpenTitle: (HubCard) -> Unit,
    onOpenGrid: (HubGrid, String) -> Unit,
    onOpenRequests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = row.row.title(tab)
    val grid = row.row.seeAll()
    MediaCarousel(
        title = title,
        modifier = modifier,
        onMoreClick =
            when {
                grid != null -> ({ onOpenGrid(grid, title) })
                row.row is HubRow.RecentRequests -> onOpenRequests
                else -> null
            },
        items = row.cards,
        itemKey = { it.kind.name + it.tmdbId },
    ) { _, card ->
        MediaCard(
            posterUrl = card.artworkUrl,
            title = card.name,
            rating = card.rating,
            onClick = { onOpenTitle(card) },
            modifier = Modifier.width(dimensionResource(DesR.dimen.card_width)),
            status = card.availability.toStatus(),
        )
    }
}

/** Genres show their backdrop under the name; studios and networks show their logo, as the web client does. */
@Composable
private fun TileRow(
    tab: HubTab,
    row: HubRowUi.Tiles,
    onOpenGrid: (HubGrid, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun open(tile: HubTile) = row.row.tileGrid(tile)?.let { onOpenGrid(it, tile.name) }
    MediaCarousel(
        title = row.row.title(tab),
        modifier = modifier,
        items = row.tiles,
        itemKey = { it.id },
    ) { _, tile ->
        if (row.row is HubRow.Genres) {
            BackdropTile(title = tile.name, imageUrl = tile.imageUrl, onClick = { open(tile) })
        } else {
            LogoTile(name = tile.name, logoUrl = tile.imageUrl, onClick = { open(tile) })
        }
    }
}

@Composable
private fun Hero(
    hero: List<HubHero>,
    onOpenTitle: (HubCard) -> Unit,
) {
    val items = hero.toHeroItems()
    HeroCarousel(
        items = items,
        modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Hub.HERO)),
        onItemClick = { id -> hero.firstOrNull { it.tmdbId == id }?.let { onOpenTitle(it.toCard()) } },
    )
}

/** A custom slider's own title, else the built-in row's name in the user's language. */
@Composable
private fun HubRow.title(tab: HubTab): String =
    this.title?.takeIf { it.isNotBlank() } ?: stringResource(
        when (this) {
            is HubRow.Trending -> R.string.hubs_row_trending
            is HubRow.RecentlyAdded -> R.string.hubs_row_recently_added
            is HubRow.RecentRequests -> R.string.hubs_row_recent_requests
            is HubRow.Watchlist -> R.string.hubs_row_watchlist
            is HubRow.Popular -> if (tab == HubTab.Movies) R.string.hubs_row_popular_movies else R.string.hubs_row_popular_tv
            is HubRow.Genres -> R.string.hubs_row_genres
            is HubRow.Studios -> R.string.hubs_row_studios
            is HubRow.Networks -> R.string.hubs_row_networks
            is HubRow.Upcoming -> if (tab == HubTab.Movies) R.string.hubs_row_upcoming_movies else R.string.hubs_row_upcoming_tv
            else -> R.string.hubs_row_custom
        },
    )

/** The one status chip a title's availability shows, on a hub card and in a search row alike; nothing when the server holds nothing of it. */
@Composable
internal fun TitleAvailability.toStatus(): MediaCardStatus? =
    when (this) {
        TitleAvailability.None -> null
        TitleAvailability.Pending -> MediaCardStatus(stringResource(R.string.hubs_card_requested), BingeSentiment.Info)
        TitleAvailability.Processing -> MediaCardStatus(stringResource(R.string.hubs_card_processing), BingeSentiment.Caution)
        TitleAvailability.PartiallyAvailable -> MediaCardStatus(stringResource(R.string.hubs_card_partial), BingeSentiment.Caution)
        TitleAvailability.Available -> MediaCardStatus(stringResource(R.string.hubs_card_available), BingeSentiment.Positive)
        TitleAvailability.Blocklisted -> MediaCardStatus(stringResource(R.string.hubs_card_blocklisted), BingeSentiment.Negative)
    }
