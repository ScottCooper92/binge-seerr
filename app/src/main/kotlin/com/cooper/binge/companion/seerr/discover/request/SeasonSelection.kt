package com.cooper.binge.companion.seerr.discover.request

import com.cooper.binge.companion.seerr.discover.TitleAvailability

/** One season of a show as the request sheet lists it. [availability] is the version being requested; only `None` is open. */
data class SeasonChoice(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val availability: TitleAvailability,
) {
    /** A season the server already holds or has been asked for is shown as covered and cannot be picked. */
    val isOpen: Boolean get() = availability == TitleAvailability.None
}

/** The show's seasons once for each version, since a season can be available in standard and open in 4K. */
data class SeasonChoices(
    val standard: List<SeasonChoice>,
    val fourK: List<SeasonChoice>,
) {
    fun of(use4k: Boolean): List<SeasonChoice> = if (use4k) fourK else standard
}

/**
 * Which seasons a request sends. With partial requests off the server takes the whole show, so the choice is made for the user
 * and [requestSeasons] is every open season. With them on, the user picks from the open ones and a covered season is never
 * selectable.
 */
data class SeasonSelection(
    val seasons: List<SeasonChoice>,
    val partialAllowed: Boolean,
    val picked: Set<Int> = emptySet(),
) {
    val open: List<SeasonChoice> get() = seasons.filter { it.isOpen }

    private val openNumbers: Set<Int> get() = open.mapTo(linkedSetOf()) { it.number }

    /** The seasons the request sends, in order. */
    val requestSeasons: List<Int>
        get() = if (partialAllowed) picked.filter { it in openNumbers }.sorted() else open.map { it.number }

    val canSubmit: Boolean get() = requestSeasons.isNotEmpty()

    val allOpenPicked: Boolean get() = openNumbers.isNotEmpty() && openNumbers.all { it in picked }

    /** Flips one season; a covered or unknown one is left as it is. */
    fun toggle(number: Int): SeasonSelection =
        when {
            !partialAllowed || number !in openNumbers -> this
            number in picked -> copy(picked = picked - number)
            else -> copy(picked = picked + number)
        }

    /** All seasons still open, or none when they are all picked already. */
    fun toggleAll(): SeasonSelection =
        when {
            !partialAllowed || openNumbers.isEmpty() -> this
            allOpenPicked -> copy(picked = emptySet())
            else -> copy(picked = openNumbers)
        }
}
