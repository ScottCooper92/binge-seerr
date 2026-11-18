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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.CarouselSkeleton
import com.binge.designsystem.component.DarkStatusBarEffect
import com.binge.designsystem.component.HeroCarousel
import com.binge.designsystem.component.HeroItem
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
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import kotlinx.coroutines.flow.first
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
                    HubRows(tab, state, inner, onOpenTitle)
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
) {
    val listState = rememberLazyListState()
    ShowHeroWhenItArrives(listState, hasHero = state.hero.isNotEmpty())
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (state.hero.isNotEmpty()) item(key = HERO_KEY) { Hero(state.hero, onOpenTitle) }
        items(state.rows, key = { it.row.sliderId }) { row ->
            MediaCarousel(
                title = row.row.title(tab),
                items = row.cards,
                itemKey = { it.kind.name + it.tmdbId },
            ) { _, card ->
                MediaCard(
                    posterUrl = card.artworkUrl,
                    title = card.name,
                    rating = null,
                    onClick = { onOpenTitle(card) },
                    modifier = Modifier.width(dimensionResource(DesR.dimen.card_width)),
                    status = card.availability.toStatus(),
                )
            }
        }
        // A row still loading is a skeleton below the ones that landed, so the page does not jump when it arrives.
        items(state.pending) { CarouselSkeleton(description = stringResource(R.string.hubs_loading_row)) }
    }
}

@Composable
private fun Hero(
    hero: List<HubHero>,
    onOpenTitle: (HubCard) -> Unit,
) {
    val items = hero.map { HeroItem(id = it.tmdbId, imageUrl = it.backdropUrl, title = it.name, rating = it.rating, year = it.year) }
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
 * inserted above it, so a hub nobody has scrolled would stay on its first row with the new hero out of sight above. Until the
 * user scrolls, a hero that arrives takes the top of the list.
 */
@Composable
private fun ShowHeroWhenItArrives(
    listState: LazyListState,
    hasHero: Boolean,
) {
    var scrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.first { it }
        scrolled = true
    }
    LaunchedEffect(hasHero) {
        if (hasHero && !scrolled) listState.scrollToItem(0)
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
