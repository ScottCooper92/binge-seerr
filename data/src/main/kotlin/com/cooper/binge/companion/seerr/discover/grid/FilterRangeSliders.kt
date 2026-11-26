package com.cooper.binge.companion.seerr.discover.grid

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** The top of the rating track. A thumb at an end means that bound is unset. */
const val RATING_SLIDER_MAX = 10f
private const val RATING_SLIDER_STEP = 0.5f

/** The stops strictly inside a 0 to 10 track in half-point steps: 20 intervals leave 19 stops between the ends. */
const val RATING_SLIDER_STEPS = (RATING_SLIDER_MAX / RATING_SLIDER_STEP).toInt() - 1

/** The top of the runtime track, in minutes. As an upper bound it means no limit. */
const val RUNTIME_SLIDER_MAX_MINUTES = 240
private const val RUNTIME_SLIDER_STEP_MINUTES = 15

/** The stops strictly inside the runtime track: 240 in 15-minute steps is 16 intervals, so 15 stops between the ends. */
const val RUNTIME_SLIDER_STEPS = RUNTIME_SLIDER_MAX_MINUTES / RUNTIME_SLIDER_STEP_MINUTES - 1

/** The top vote-count stop. As an upper bound it means no limit. */
const val VOTE_COUNT_MAX = 50_000

/**
 * The vote-count stops. The useful range spans four orders of magnitude, so a linear track would put every useful value in its
 * first pixel: the slider steps through these named stops instead, and a position is a stop's index.
 */
val VoteSliderStops: List<Int> = listOf(0, 10, 100, 1_000, 10_000, VOTE_COUNT_MAX)

/** The last stop's index, the top of the track. */
val VOTE_SLIDER_LAST_INDEX: Int = VoteSliderStops.lastIndex

/** The stops strictly inside the vote track. */
val VOTE_SLIDER_STEPS: Int = VoteSliderStops.size - 2

/** Where the rating thumbs sit, derived from the nullable bounds so an unset bound is an end of the track. */
fun DiscoverFilters.ratingSliderPosition(): ClosedFloatingPointRange<Float> {
    val start = (ratingMin?.toFloat() ?: 0f).coerceIn(0f, RATING_SLIDER_MAX)
    val end = (ratingMax?.toFloat() ?: RATING_SLIDER_MAX).coerceIn(start, RATING_SLIDER_MAX)
    return start..end
}

/** The bounds a rating thumb [position] means: an end of the track is null, and a thumb that has not moved keeps its bound. */
fun DiscoverFilters.withRatingSliderPosition(position: ClosedFloatingPointRange<Float>): DiscoverFilters {
    val current = ratingSliderPosition()
    return copy(
        ratingMin = if (position.start == current.start) ratingMin else position.start.takeIf { it > 0f }?.toDouble(),
        ratingMax =
            if (position.endInclusive ==
                current.endInclusive
            ) {
                ratingMax
            } else {
                position.endInclusive.takeIf { it < RATING_SLIDER_MAX }?.toDouble()
            },
    )
}

/** Where the runtime thumbs sit: a bound outside the track is drawn at its end. */
fun DiscoverFilters.runtimeSliderPosition(): ClosedFloatingPointRange<Float> {
    val start = (runtimeMin ?: 0).coerceIn(0, RUNTIME_SLIDER_MAX_MINUTES)
    val end = (runtimeMax ?: RUNTIME_SLIDER_MAX_MINUTES).coerceIn(start, RUNTIME_SLIDER_MAX_MINUTES)
    return start.toFloat()..end.toFloat()
}

/** The bounds a runtime thumb [position] means: an end of the track is null, never 0 or the maximum. */
fun DiscoverFilters.withRuntimeSliderPosition(position: ClosedFloatingPointRange<Float>): DiscoverFilters =
    copy(
        runtimeMin = position.start.roundToInt().takeIf { it > 0 },
        runtimeMax = position.endInclusive.roundToInt().takeIf { it < RUNTIME_SLIDER_MAX_MINUTES },
    )

/** A vote count as a (possibly fractional) stop index, interpolating linearly between neighbouring stops. */
fun voteSliderIndex(votes: Int): Float {
    val clamped = votes.coerceIn(0, VOTE_COUNT_MAX)
    val below = VoteSliderStops.indexOfLast { it <= clamped }.coerceAtMost(VOTE_SLIDER_LAST_INDEX - 1)
    val span = VoteSliderStops[below + 1] - VoteSliderStops[below]
    return (below + (clamped - VoteSliderStops[below]).toDouble() / span).toFloat()
}

/** The vote count at a stop [index], interpolating between stops for a fractional one; the inverse of [voteSliderIndex]. */
fun voteAtSliderIndex(index: Float): Int {
    val clamped = index.toDouble().coerceIn(0.0, VOTE_SLIDER_LAST_INDEX.toDouble())
    val below = clamped.toInt().coerceAtMost(VOTE_SLIDER_LAST_INDEX - 1)
    val span = VoteSliderStops[below + 1] - VoteSliderStops[below]
    return (VoteSliderStops[below] + (clamped - below) * span).roundToInt()
}

/** Where the vote-count thumbs sit, as stop indexes. */
fun DiscoverFilters.voteSliderPosition(): ClosedFloatingPointRange<Float> {
    val start = voteSliderIndex(voteCountMin ?: 0)
    val end = voteSliderIndex(voteCountMax ?: VOTE_COUNT_MAX).coerceAtLeast(start)
    return start..end
}

/** The bounds a vote thumb [position] means: an end of the track is null, and a thumb that has not moved keeps its bound. */
fun DiscoverFilters.withVoteSliderPosition(position: ClosedFloatingPointRange<Float>): DiscoverFilters {
    val current = voteSliderPosition()
    return copy(
        voteCountMin = if (position.start == current.start) voteCountMin else voteAtSliderIndex(position.start).takeIf { it > 0 },
        voteCountMax =
            if (position.endInclusive == current.endInclusive) {
                voteCountMax
            } else {
                voteAtSliderIndex(position.endInclusive).takeIf { it < VOTE_COUNT_MAX }
            },
    )
}

/** TMDB's earliest data; the date picker offers nothing before it. */
const val RELEASE_MIN_YEAR = 1874

/** How far past the current year the picker reaches, since an announced title has a future release date. */
const val RELEASE_FUTURE_YEARS = 10

/** The years the release date picker offers on [today]. */
fun releaseYearRange(today: LocalDate): IntRange = RELEASE_MIN_YEAR..(today.year + RELEASE_FUTURE_YEARS)

/**
 * Sets the release window's start to the first day of [month]. A start after the current end swaps the two, snapped to month
 * boundaries, rather than handing the server a start after the end.
 */
fun DiscoverFilters.withReleaseFromMonth(month: YearMonth): DiscoverFilters {
    val from = month.atDay(1)
    val to = releaseTo?.let(LocalDate::parse)
    return if (to != null && from > to) {
        copy(releaseFrom = YearMonth.from(to).atDay(1).toString(), releaseTo = month.atEndOfMonth().toString())
    } else {
        copy(releaseFrom = from.toString())
    }
}

/** Sets the end to the last day of [month]; an end before the current start swaps as [withReleaseFromMonth] does. */
fun DiscoverFilters.withReleaseToMonth(month: YearMonth): DiscoverFilters {
    val to = month.atEndOfMonth()
    val from = releaseFrom?.let(LocalDate::parse)
    return if (from != null && to < from) {
        copy(releaseFrom = month.atDay(1).toString(), releaseTo = YearMonth.from(from).atEndOfMonth().toString())
    } else {
        copy(releaseTo = to.toString())
    }
}
