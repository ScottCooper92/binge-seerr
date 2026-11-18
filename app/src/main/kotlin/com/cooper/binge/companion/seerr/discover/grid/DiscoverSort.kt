package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import kotlinx.serialization.Serializable

/**
 * The orders the server's discover routes accept, as its `sortBy` values. The movie and TV lists share the first six; a
 * movie adds revenue and a title, a show adds a title of its own, and each has its own date.
 */
@Serializable
enum class DiscoverSort(
    val value: String,
    private val movie: Boolean = true,
    private val series: Boolean = true,
) {
    PopularityDesc("popularity.desc"),
    PopularityAsc("popularity.asc"),
    RatingDesc("vote_average.desc"),
    RatingAsc("vote_average.asc"),
    VotesDesc("vote_count.desc"),
    VotesAsc("vote_count.asc"),
    ReleaseNewest("release_date.desc", series = false),
    ReleaseOldest("release_date.asc", series = false),
    FirstAirNewest("first_air_date.desc", movie = false),
    FirstAirOldest("first_air_date.asc", movie = false),
    RevenueDesc("revenue.desc", series = false),
    RevenueAsc("revenue.asc", series = false),
    TitleAsc("original_title.asc", series = false),
    TitleDesc("original_title.desc", series = false),
    NameAsc("original_name.asc", movie = false),
    NameDesc("original_name.desc", movie = false),
    ;

    /** Whether this orders by the average vote, which a handful of votes can swing. */
    val isByRating: Boolean get() = this == RatingDesc || this == RatingAsc

    fun appliesTo(kind: TitleKind): Boolean = if (kind == TitleKind.Series) series else movie

    companion object {
        /** The orders the list of [kind] can be put in, in the order a picker offers them. */
        fun forKind(kind: TitleKind): List<DiscoverSort> = entries.filter { it.appliesTo(kind) }

        /** The orders the filter sheet offers, as Binge's sort choices are: not every order the server takes. */
        fun sheetChoices(kind: TitleKind): List<DiscoverSort> =
            if (kind == TitleKind.Series) {
                listOf(PopularityDesc, RatingDesc, FirstAirNewest, FirstAirOldest, NameAsc)
            } else {
                listOf(PopularityDesc, RatingDesc, ReleaseNewest, ReleaseOldest, TitleAsc)
            }

        /** The date order a preset starts in: the movie's release date, or the show's first air date. */
        fun soonest(kind: TitleKind): DiscoverSort = if (kind == TitleKind.Series) FirstAirOldest else ReleaseOldest
    }
}
