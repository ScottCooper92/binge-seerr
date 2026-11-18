package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.CarouselSkeleton
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.navOverlayPadding
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.binge.designsystem.R as DesR

private const val SKELETON_ROWS = 3

/**
 * One discovery hub, Movies or TV: the server's rows of posters, each loading on its own. [onOpenTitle] gets the card the user
 * tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverHubScreen(
    tab: HubTab,
    state: DiscoverHubUiState,
    onRefresh: () -> Unit,
    onOpenTitle: (HubCard) -> Unit,
) {
    BingeScreenScaffold(
        bar = ScreenBar.Small,
        title = stringResource(if (tab == HubTab.Movies) R.string.hubs_title_movies else R.string.hubs_title_tv),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val inner = navOverlayPadding(PaddingValues())
            when (state) {
                DiscoverHubUiState.Loading -> HubSkeleton(inner)
                DiscoverHubUiState.Empty -> EmptyScreen(stringResource(R.string.hubs_empty_message), Modifier.padding(inner))
                is DiscoverHubUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry = onRefresh)
                is DiscoverHubUiState.Ready ->
                    BingePullToRefresh(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        HubRows(tab, state, inner, onOpenTitle)
                    }
            }
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
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
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
