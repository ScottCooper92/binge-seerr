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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.component.BackdropTile
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.CarouselSkeleton
import com.binge.designsystem.component.DarkStatusBarEffect
import com.binge.designsystem.component.HeroCarousel
import com.binge.designsystem.component.HeroItem
import com.binge.designsystem.component.LogoTile
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.component.StatusBarScrim
import com.binge.designsystem.component.StatusChip
import com.binge.designsystem.navOverlayPadding
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.HubTile
import com.cooper.binge.companion.seerr.discover.sliders.seeAll
import com.cooper.binge.companion.seerr.discover.sliders.tileGrid
import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.binge.designsystem.R as DesR

private const val SKELETON_ROWS = 3
private const val HERO_KEY = "hero"

/**
 * One discovery hub, Movies or TV: the server's rows of posters, each loading on its own. [onOpenTitle] gets the card the user
 * tapped.
 *
 * The hub has no title bar: the tab bar says where you are, and the hero runs edge to edge from the top of the screen, under the
 * status bar, whose icons stay light over the backdrop with a dark wash at the top edge for when the rows scroll up under it.
 * Without a hero the rows start below the status bar instead. The hub's name is its pane title, for a screen reader.
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
    val inner = navOverlayPadding(if (hasHero) PaddingValues() else PaddingValues(top = statusBar))
    Box(modifier = Modifier.fillMaxSize().semantics { paneTitle = title }) {
        when (state) {
            DiscoverHubUiState.Loading -> HubSkeleton(inner)
            DiscoverHubUiState.Empty -> EmptyScreen(stringResource(R.string.hubs_empty_message), Modifier.padding(inner))
            is DiscoverHubUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry = onRefresh)
            is DiscoverHubUiState.Ready ->
                BingePullToRefresh(isRefreshing = state.refreshing, onRefresh = onRefresh, indicatorTopInset = statusBar) {
                    HubRows(tab, state, inner, onOpenTitle, onOpenGrid, onOpenRequests)
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
        items(SKELETON_ROWS) { CarouselSkeleton(description = stringResource(R.string.hubs_loading_row)) }
    }
}

@Composable
private fun HubRows(
    tab: HubTab,
    state: DiscoverHubUiState.Ready,
    contentPadding: PaddingValues,
    onOpenTitle: (HubCard) -> Unit,
    onOpenGrid: (HubGrid, String) -> Unit,
    onOpenRequests: () -> Unit,
) {
    val listState = rememberLazyListState()
    ShowHeroWhenItArrives(listState, hasHero = state.hero.isNotEmpty())
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (state.hero.isNotEmpty()) item(key = HERO_KEY) { Hero(state.hero, onOpenTitle) }
        items(state.rows, key = { it.row.sliderId }) { row ->
            when (row) {
                is HubRowUi.Cards -> CardRow(tab, row, onOpenTitle, onOpenGrid, onOpenRequests)
                is HubRowUi.Tiles -> TileRow(tab, row, onOpenGrid)
            }
        }
        // A row still loading is a skeleton below the ones that landed, so the page does not jump when it arrives.
        items(state.pending) { CarouselSkeleton(description = stringResource(R.string.hubs_loading_row)) }
    }
}

@Composable
private fun CardRow(
    tab: HubTab,
    row: HubRowUi.Cards,
    onOpenTitle: (HubCard) -> Unit,
    onOpenGrid: (HubGrid, String) -> Unit,
    onOpenRequests: () -> Unit,
) {
    val title = row.row.title(tab)
    val grid = row.row.seeAll()
    MediaCarousel(
        title = title,
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
) {
    fun open(tile: HubTile) = row.row.tileGrid(tile)?.let { onOpenGrid(it, tile.name) }
    MediaCarousel(
        title = row.row.title(tab),
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
    val items =
        hero.map {
            HeroItem(id = it.tmdbId, imageUrl = it.backdropUrl, title = it.name, rating = null, genres = it.genres, rank = it.rank)
        }
    HeroCarousel(
        items = items,
        onItemClick = { id -> hero.firstOrNull { it.tmdbId == id }?.let { onOpenTitle(it.toCard()) } },
        heroActions = { item ->
            hero
                .firstOrNull { it.tmdbId == item.id }
                ?.availability
                ?.toStatus()
                ?.let { StatusChip(it.label, it.sentiment) }
        },
    )
}

/**
 * The hero loads apart from the rows and can land after them. A list keeps the item it shows first in place when something is
 * inserted above it, so a hub sitting at its very top would stay on its first row with the new hero out of sight above. A hero
 * that arrives while the list was at its top takes the top; one that arrives while the user has scrolled leaves them where they
 * are. The position is read before the hero is in, since inserting it is what moves the list, and a pull to refresh at the top
 * does not leave the top. A hub restored with its hero already there is left alone.
 */
@Composable
private fun ShowHeroWhenItArrives(
    listState: LazyListState,
    hasHero: Boolean,
) {
    val heroNow by rememberUpdatedState(hasHero)
    var atTopBeforeHero by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .collect { atTop -> if (!heroNow) atTopBeforeHero = atTop }
    }
    // Only a hero that arrives while this composition is watching counts. A hub composed again with its hero already there (a
    // rotation, a restore) has a list state that came back with its own position, which is not this one's to reset.
    var heroSeen by remember { mutableStateOf(hasHero) }
    LaunchedEffect(hasHero) {
        if (hasHero && !heroSeen && atTopBeforeHero) listState.scrollToItem(0)
        heroSeen = hasHero
    }
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
