package com.cooper.binge.companion.seerr.discover.grid

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The release windows the filter sheet offers beside a custom range, as Binge's year chips do. */
enum class ReleasePreset {
    AnyYear,
    Upcoming,
    ThisYear,
    LastFiveYears,
    ;

    /** The from and to dates this window means on [today], either of which may be open. */
    fun range(today: LocalDate): Pair<String?, String?> =
        when (this) {
            AnyYear -> null to null
            Upcoming -> today.plusDays(1).toString() to null
            ThisYear -> today.withDayOfYear(1).toString() to today.with(TemporalAdjusters.lastDayOfYear()).toString()
            LastFiveYears -> today.minusYears(LAST_YEARS).toString() to today.toString()
        }

    companion object {
        private const val LAST_YEARS = 5L

        /** The window the two dates are exactly, or null for a custom range. */
        fun of(
            from: String?,
            to: String?,
            today: LocalDate,
        ): ReleasePreset? = entries.firstOrNull { it.range(today) == (from to to) }
    }
}

/** These filters with the release window set to [preset]. */
fun DiscoverFilters.withRelease(
    preset: ReleasePreset,
    today: LocalDate,
): DiscoverFilters {
    val (from, to) = preset.range(today)
    return copy(releaseFrom = from, releaseTo = to)
}
