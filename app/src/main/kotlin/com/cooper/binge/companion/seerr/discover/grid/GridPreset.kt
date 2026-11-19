package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleSource
import kotlinx.serialization.Serializable

/**
 * What a "see all" opens the grid on: the list a hub row shows, as the starting filter of the grid. Serializable, because it
 * is part of a route that is saved across process death.
 */
@Serializable
sealed interface GridPreset {
    @Serializable
    data object Popular : GridPreset

    @Serializable
    data object Upcoming : GridPreset

    /** Trending and the watchlist take only a page, so their grid has no sort and no filters. */
    @Serializable
    data object Trending : GridPreset

    @Serializable
    data object Watchlist : GridPreset

    /** The signed-in user's whole watchlist, movies and series together, as the user page opens it. */
    @Serializable
    data object OwnWatchlist : GridPreset

    @Serializable
    data class Genre(
        val id: Int,
    ) : GridPreset

    /** A studio for a movie, a network for a series. */
    @Serializable
    data class Company(
        val id: Int,
    ) : GridPreset

    @Serializable
    data class Language(
        val code: String,
    ) : GridPreset

    @Serializable
    data class Keyword(
        val id: Int,
    ) : GridPreset

    @Serializable
    data class Provider(
        val id: Int,
        val region: String,
    ) : GridPreset
}

/** Whether the list can be sorted and filtered at all, or is one plain paged call. */
val GridPreset.isFilterable: Boolean get() = this != GridPreset.Trending && this != GridPreset.Watchlist && this != GridPreset.OwnWatchlist

/** The filters a preset starts the grid with. */
fun GridPreset.startingFilters(today: String): DiscoverFilters =
    when (this) {
        GridPreset.Popular, GridPreset.Trending, GridPreset.Watchlist, GridPreset.OwnWatchlist -> DiscoverFilters()
        GridPreset.Upcoming -> DiscoverFilters(releaseFrom = today)
        is GridPreset.Genre -> DiscoverFilters(genres = setOf(id))
        is GridPreset.Company -> DiscoverFilters(company = id)
        is GridPreset.Language -> DiscoverFilters(language = code)
        is GridPreset.Keyword -> DiscoverFilters(keywords = setOf(id))
        is GridPreset.Provider -> DiscoverFilters(providers = setOf(id), region = region)
    }

/** The order a preset starts in: soonest first for what is yet to come, otherwise the most popular. */
fun GridPreset.startingSort(kind: TitleKind): DiscoverSort =
    when (this) {
        GridPreset.Upcoming -> DiscoverSort.soonest(kind)
        else -> DiscoverSort.PopularityDesc
    }

/** The dedicated route for a preset: what a server with no filter parameters is asked, and the lists that take none. */
fun GridPreset.toSource(kind: TitleKind): TitleSource =
    when (this) {
        GridPreset.Popular -> TitleSource.Popular(kind)
        GridPreset.Upcoming -> TitleSource.Upcoming(kind)
        GridPreset.Trending -> TitleSource.Trending
        GridPreset.Watchlist, GridPreset.OwnWatchlist -> TitleSource.Watchlist
        is GridPreset.Genre -> TitleSource.ByGenre(kind, id)
        is GridPreset.Company -> TitleSource.ByCompany(kind, id)
        is GridPreset.Language -> TitleSource.ByLanguage(kind, code)
        is GridPreset.Keyword -> TitleSource.ByKeyword(kind, id)
        is GridPreset.Provider -> TitleSource.Popular(kind)
    }
