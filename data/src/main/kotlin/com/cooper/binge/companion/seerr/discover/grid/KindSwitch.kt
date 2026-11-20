package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind

/**
 * What survives switching a grid from movies to shows or back. A genre, a studio or network, a status and a certification are
 * each one kind's own, so they go. What a title is, whichever kind it is, stays: keywords, the original language, the dates,
 * the runtime, the rating, the votes and the streaming services.
 */
fun DiscoverFilters.carriedToOtherKind(): DiscoverFilters =
    copy(
        genres = emptySet(),
        company = null,
        status = emptySet(),
        certificationCountry = null,
        certification = null,
    )

/** The preset a grid reopens on in the other kind: the ones that name one kind's own id fall back to the most popular. */
fun GridPreset.carriedToOtherKind(): GridPreset =
    when (this) {
        is GridPreset.Genre, is GridPreset.Company -> GridPreset.Popular
        else -> this
    }

/** The order in the other kind: the date and title orders have a name for each kind, and revenue has no show to rank. */
fun DiscoverSort.carriedTo(kind: TitleKind): DiscoverSort =
    when {
        appliesTo(kind) -> this
        this == DiscoverSort.ReleaseNewest -> DiscoverSort.FirstAirNewest
        this == DiscoverSort.FirstAirNewest -> DiscoverSort.ReleaseNewest
        this == DiscoverSort.ReleaseOldest -> DiscoverSort.FirstAirOldest
        this == DiscoverSort.FirstAirOldest -> DiscoverSort.ReleaseOldest
        this == DiscoverSort.TitleAsc -> DiscoverSort.NameAsc
        this == DiscoverSort.NameAsc -> DiscoverSort.TitleAsc
        this == DiscoverSort.TitleDesc -> DiscoverSort.NameDesc
        this == DiscoverSort.NameDesc -> DiscoverSort.TitleDesc
        else -> DiscoverSort.PopularityDesc
    }
