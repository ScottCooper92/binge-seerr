package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset

/** What the paged grid opens on: the [kind] of title and the [preset] that becomes its starting filter. */
data class HubGrid(
    val kind: TitleKind,
    val preset: GridPreset,
)

/**
 * The grid a row's "see all" opens, or null for a row with none: recently added is the server's own list, recent requests opens
 * the request browser instead of the grid, and a custom search, a keyword set or a streaming set of several ids has no single
 * preset to start from.
 */
fun HubRow.seeAll(): HubGrid? =
    when (this) {
        is HubRow.Trending -> HubGrid(tab.titleKind(), GridPreset.Trending)
        is HubRow.Watchlist -> HubGrid(tab.titleKind(), GridPreset.Watchlist)
        is HubRow.Popular -> HubGrid(tab.titleKind(), GridPreset.Popular)
        is HubRow.Upcoming -> HubGrid(tab.titleKind(), GridPreset.Upcoming)
        is HubRow.Genre -> HubGrid(tab.titleKind(), GridPreset.Genre(genreId))
        is HubRow.Studio -> HubGrid(TitleKind.Movie, GridPreset.Company(studioId))
        is HubRow.Network -> HubGrid(TitleKind.Series, GridPreset.Company(networkId))
        is HubRow.Keywords -> keywordIds.singleOrNull()?.let { HubGrid(tab.titleKind(), GridPreset.Keyword(it)) }
        is HubRow.Streaming -> providerIds.singleOrNull()?.let { HubGrid(tab.titleKind(), GridPreset.Provider(it, region)) }
        is HubRow.RecentlyAdded, is HubRow.RecentRequests, is HubRow.Search, is HubRow.Genres, is HubRow.Studios, is HubRow.Networks -> null
    }

/** The grid a tap on [tile] of a genre, studio or network row opens. */
fun HubRow.tileGrid(tile: HubTile): HubGrid? =
    when (this) {
        is HubRow.Genres -> HubGrid(tab.titleKind(), GridPreset.Genre(tile.id))
        is HubRow.Studios -> HubGrid(TitleKind.Movie, GridPreset.Company(tile.id))
        is HubRow.Networks -> HubGrid(TitleKind.Series, GridPreset.Company(tile.id))
        else -> null
    }
