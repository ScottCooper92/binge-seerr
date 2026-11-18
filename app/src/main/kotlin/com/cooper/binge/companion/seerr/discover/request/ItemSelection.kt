package com.cooper.binge.companion.seerr.discover.request

/** One thing a request can include. [open] is false for one the server already holds, has been asked for, or has blocked. */
data class SelectableItem(
    val id: Int,
    val open: Boolean,
)

/**
 * Which of several items a request sends, under an optional quota [limit] on how many may be picked. A closed item is shown but
 * never picked. It knows nothing of what the items are, so a list of films and a list of seasons can use the same rules.
 */
data class ItemSelection(
    val items: List<SelectableItem>,
    val picked: Set<Int> = emptySet(),
    val limit: Int? = null,
) {
    private val openIds: Set<Int> get() = items.filter { it.open }.mapTo(linkedSetOf()) { it.id }

    val openCount: Int get() = openIds.size

    /** The items the request sends, in the list's order. */
    val requestIds: List<Int> get() = items.map { it.id }.filter { it in picked && it in openIds }

    val canSubmit: Boolean get() = requestIds.isNotEmpty()

    /** Another open item can still be picked: the quota has room for it. */
    val canPickMore: Boolean get() = limit == null || requestIds.size < limit

    /** "All" is offered while the quota has room for every open item. */
    val canPickAll: Boolean get() = limit == null || openCount <= limit

    val allOpenPicked: Boolean get() = openIds.isNotEmpty() && openIds.all { it in picked }

    /** Flips one item; a closed or unknown one is left as it is, and so is a pick the quota has no room for. */
    fun toggle(id: Int): ItemSelection =
        when {
            id !in openIds -> this
            id in picked -> copy(picked = picked - id)
            !canPickMore -> this
            else -> copy(picked = picked + id)
        }

    /** Every open item, or none when they are all picked already; unchanged where the quota cannot take them all. */
    fun toggleAll(): ItemSelection =
        when {
            openIds.isEmpty() || !canPickAll -> this
            allOpenPicked -> copy(picked = emptySet())
            else -> copy(picked = openIds)
        }

    /** What is ticked before the user touches anything: every open item where the quota covers them all, otherwise none. */
    fun preselected(): ItemSelection = if (canPickAll) copy(picked = openIds) else copy(picked = emptySet())
}
