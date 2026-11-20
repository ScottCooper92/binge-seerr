package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource

/** The kind of title a hub shows. */
fun HubTab.titleKind(): TitleKind = if (this == HubTab.Movies) TitleKind.Movie else TitleKind.Series

/**
 * The title list a row is read from, or null for a row that is not one: the tile rows (genres, studios, networks) and the
 * two that come from the server's media table rather than a title list (recently added, recent requests).
 */
internal fun HubRow.toSource(): TitleSource? =
    when (this) {
        is HubRow.Trending -> TitleSource.Trending
        is HubRow.Watchlist -> TitleSource.Watchlist
        is HubRow.Search -> TitleSource.Search(query)
        is HubRow.Popular -> TitleSource.Popular(tab.titleKind())
        is HubRow.Upcoming -> TitleSource.Upcoming(tab.titleKind())
        is HubRow.Genre -> TitleSource.ByGenre(tab.titleKind(), genreId)
        is HubRow.Studio -> TitleSource.ByCompany(TitleKind.Movie, studioId)
        is HubRow.Network -> TitleSource.ByCompany(TitleKind.Series, networkId)
        is HubRow.Keywords -> TitleSource.ByKeywords(tab.titleKind(), keywordIds)
        is HubRow.Streaming -> TitleSource.ByStreaming(tab.titleKind(), region, providerIds)
        is HubRow.RecentlyAdded,
        is HubRow.RecentRequests,
        is HubRow.Genres,
        is HubRow.Studios,
        is HubRow.Networks,
        -> null
    }

/** Most cards a hub row shows; the rest is the grid's. */
internal const val HUB_ROW_CARDS = 10

/**
 * What a hub row keeps of a page: the titles of the hub's own kind (a mixed list holds people, collections and the other
 * kind too), at most [HUB_ROW_CARDS].
 */
internal fun List<TitleResult>.forHub(tab: HubTab): List<TitleResult> = filter { it.kind == tab.titleKind() }.take(HUB_ROW_CARDS)
