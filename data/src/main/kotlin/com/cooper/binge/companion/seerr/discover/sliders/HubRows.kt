package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSlider
import com.cooper.binge.companion.seerr.ui.settings.server.SliderType
import com.cooper.binge.companion.seerr.ui.settings.server.tagIds
import com.cooper.binge.companion.seerr.ui.settings.server.toStreamingPick

/** The two hubs. Each shows the sliders that belong to it, in the server's order. */
enum class HubTab { Movies, Series }

/**
 * One row of a hub, as a description of what to load and not how to draw it. [sliderId] is the slider it came from and
 * [title] its own title, which a custom slider has and a built-in one does not.
 */
sealed interface HubRow {
    val sliderId: Int
    val title: String?

    /** A list of both kinds that this hub narrows to its own: [tab] is the media-type filter. */
    sealed interface Mixed : HubRow {
        val tab: HubTab
    }

    data class Trending(
        override val sliderId: Int,
        override val title: String?,
        override val tab: HubTab,
    ) : Mixed

    data class RecentlyAdded(
        override val sliderId: Int,
        override val title: String?,
        override val tab: HubTab,
    ) : Mixed

    data class RecentRequests(
        override val sliderId: Int,
        override val title: String?,
        override val tab: HubTab,
    ) : Mixed

    data class Watchlist(
        override val sliderId: Int,
        override val title: String?,
        override val tab: HubTab,
    ) : Mixed

    /** A custom search; [query] is what the admin typed. */
    data class Search(
        override val sliderId: Int,
        override val title: String?,
        override val tab: HubTab,
        val query: String,
    ) : Mixed

    data class Popular(
        override val sliderId: Int,
        override val title: String?,
        val tab: HubTab,
    ) : HubRow

    data class Upcoming(
        override val sliderId: Int,
        override val title: String?,
        val tab: HubTab,
    ) : HubRow

    /** The genre tiles. */
    data class Genres(
        override val sliderId: Int,
        override val title: String?,
        val tab: HubTab,
    ) : HubRow

    /** The movie studios: Movies only. */
    data class Studios(
        override val sliderId: Int,
        override val title: String?,
    ) : HubRow

    /** The TV networks: TV only. */
    data class Networks(
        override val sliderId: Int,
        override val title: String?,
    ) : HubRow

    data class Keywords(
        override val sliderId: Int,
        override val title: String?,
        val tab: HubTab,
        val keywordIds: List<Int>,
    ) : HubRow

    data class Genre(
        override val sliderId: Int,
        override val title: String?,
        val tab: HubTab,
        val genreId: Int,
    ) : HubRow

    data class Studio(
        override val sliderId: Int,
        override val title: String?,
        val studioId: Int,
    ) : HubRow

    data class Network(
        override val sliderId: Int,
        override val title: String?,
        val networkId: Int,
    ) : HubRow

    /** The titles on some streaming services in a region. */
    data class Streaming(
        override val sliderId: Int,
        override val title: String?,
        val tab: HubTab,
        val region: String,
        val providerIds: List<Int>,
    ) : HubRow
}

/**
 * The rows of [tab], from the server's slider list: disabled sliders are dropped, the server's order is kept, a kind that
 * belongs to the other hub is left to it, and a slider of a kind this app does not know, or whose data does not parse, is
 * dropped from the hub (the editor still lists it). A mixed kind goes to both hubs, narrowed to the one asking.
 */
fun List<DiscoverSlider>.rowsFor(tab: HubTab): List<HubRow> = filter { it.enabled }.mapNotNull { it.toRow(tab) }

private fun DiscoverSlider.toRow(tab: HubTab): HubRow? {
    val type = type ?: return null
    val data = data.orEmpty()
    val movies = tab == HubTab.Movies
    return when (type) {
        SliderType.Trending -> HubRow.Trending(id, title, tab)
        SliderType.RecentlyAdded -> HubRow.RecentlyAdded(id, title, tab)
        SliderType.RecentRequests -> HubRow.RecentRequests(id, title, tab)
        SliderType.Watchlist -> HubRow.Watchlist(id, title, tab)
        SliderType.Search -> data.trim().takeIf { it.isNotEmpty() }?.let { HubRow.Search(id, title, tab, it) }
        SliderType.PopularMovies -> HubRow.Popular(id, title, tab).takeIf { movies }
        SliderType.UpcomingMovies -> HubRow.Upcoming(id, title, tab).takeIf { movies }
        SliderType.MovieGenres -> HubRow.Genres(id, title, tab).takeIf { movies }
        SliderType.Studios -> HubRow.Studios(id, title).takeIf { movies }
        SliderType.PopularTv -> HubRow.Popular(id, title, tab).takeIf { !movies }
        SliderType.UpcomingTv -> HubRow.Upcoming(id, title, tab).takeIf { !movies }
        SliderType.TvGenres -> HubRow.Genres(id, title, tab).takeIf { !movies }
        SliderType.Networks -> HubRow.Networks(id, title).takeIf { !movies }
        SliderType.MovieKeyword -> keywords(tab, data).takeIf { movies }
        SliderType.TvKeyword -> keywords(tab, data).takeIf { !movies }
        SliderType.MovieGenre ->
            data
                .trim()
                .toIntOrNull()
                ?.let { HubRow.Genre(id, title, tab, it) }
                .takeIf { movies }
        SliderType.TvGenre ->
            data
                .trim()
                .toIntOrNull()
                ?.let { HubRow.Genre(id, title, tab, it) }
                .takeIf { !movies }
        SliderType.Studio ->
            data
                .trim()
                .toIntOrNull()
                ?.let { HubRow.Studio(id, title, it) }
                .takeIf { movies }
        SliderType.Network ->
            data
                .trim()
                .toIntOrNull()
                ?.let { HubRow.Network(id, title, it) }
                .takeIf { !movies }
        SliderType.MovieStreamingServices -> streaming(tab, data).takeIf { movies }
        SliderType.TvStreamingServices -> streaming(tab, data).takeIf { !movies }
    }
}

private fun DiscoverSlider.keywords(
    tab: HubTab,
    data: String,
): HubRow? = data.tagIds().takeIf { it.isNotEmpty() }?.let { HubRow.Keywords(id, title, tab, it) }

private fun DiscoverSlider.streaming(
    tab: HubTab,
    data: String,
): HubRow? =
    data.toStreamingPick().takeIf { it.region.isNotBlank() && it.providerIds.isNotEmpty() }?.let {
        HubRow.Streaming(id, title, tab, it.region, it.providerIds)
    }
