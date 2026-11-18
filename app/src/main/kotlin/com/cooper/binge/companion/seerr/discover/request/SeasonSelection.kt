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
 * selectable. [limit] is how many seasons the user's quota has left, null for none: the server refuses a request for more.
 * The picking rules are [ItemSelection]'s, which the films of a collection share.
 */
data class SeasonSelection(
    val seasons: List<SeasonChoice>,
    val partialAllowed: Boolean,
    val picked: Set<Int> = emptySet(),
    val limit: Int? = null,
) {
    private val items: ItemSelection get() = ItemSelection(seasons.map { SelectableItem(it.number, it.isOpen) }, picked, limit)

    val open: List<SeasonChoice> get() = seasons.filter { it.isOpen }

    /** The seasons the request sends, in order. */
    val requestSeasons: List<Int>
        get() = if (partialAllowed) items.requestIds.sorted() else open.map { it.number }

    /** The whole show is asked for and the quota cannot take it, so the request would be refused. */
    val overLimit: Boolean get() = !partialAllowed && !items.canPickAll

    val canSubmit: Boolean get() = requestSeasons.isNotEmpty() && !overLimit

    /** Another open season can still be picked: the quota has room for it. */
    val canPickMore: Boolean get() = items.canPickMore

    /** "All seasons" is offered while the quota has room for every open one. */
    val canPickAll: Boolean get() = items.canPickAll

    val allOpenPicked: Boolean get() = items.allOpenPicked

    /** Flips one season; a covered or unknown one is left as it is. */
    fun toggle(number: Int): SeasonSelection = if (partialAllowed) copy(picked = items.toggle(number).picked) else this

    /** All seasons still open, or none when they are all picked already. */
    fun toggleAll(): SeasonSelection = if (partialAllowed) copy(picked = items.toggleAll().picked) else this
}
