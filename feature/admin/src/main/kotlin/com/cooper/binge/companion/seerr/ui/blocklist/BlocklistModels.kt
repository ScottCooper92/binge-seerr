package com.cooper.binge.companion.seerr.ui.blocklist

import com.cooper.binge.companion.seerr.seerr.SeerrError

/** The browser's filters, each carrying the server's `filter` value; [All] sends none. */
enum class BlocklistFilter(
    val apiValue: String?,
) {
    All(null),
    Manual("manual"),
    Tagged("blocklistedTags"),
}

/** The per-filter totals on the chips; null while unknown, so a chip reads as its bare label. */
data class BlocklistCounts(
    val all: Int?,
    val manual: Int?,
    val tagged: Int?,
) {
    fun countFor(filter: BlocklistFilter): Int? =
        when (filter) {
            BlocklistFilter.All -> all
            BlocklistFilter.Manual -> manual
            BlocklistFilter.Tagged -> tagged
        }
}

sealed interface BlocklistUiState {
    data object Loading : BlocklistUiState

    data class Ready(
        val filter: BlocklistFilter,
        val search: String,
        val counts: BlocklistCounts?,
        /** Bumped by a removal; each filter's page refreshes once it trails this. */
        val listVersion: Int,
        /** Seerr 3.0+: the manual and tagged chips; Jellyseerr 2.x has one list. */
        val hasFilters: Boolean,
        val canManage: Boolean,
        val actingTmdbIds: Set<Int>,
    ) : BlocklistUiState {
        /** Per page, not per selection: an empty page says why it is empty, and a swiped-to page is its own. */
        fun isFiltered(filter: BlocklistFilter): Boolean = filter != BlocklistFilter.All || search.isNotBlank()
    }

    /** The signed-in user could not be read, so what they may do to the list is unknown. */
    data class Error(
        val error: SeerrError,
    ) : BlocklistUiState
}

sealed interface BlocklistEvent {
    data object Removed : BlocklistEvent

    data class Failed(
        val error: SeerrError,
    ) : BlocklistEvent
}
