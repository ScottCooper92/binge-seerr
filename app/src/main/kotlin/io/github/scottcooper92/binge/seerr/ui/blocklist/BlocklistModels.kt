package io.github.scottcooper92.binge.seerr.ui.blocklist

import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

/** The browser's filters, each carrying the server's `filter` value; [All] sends none. */
enum class BlocklistFilter(
    val apiValue: String?,
) {
    All(null),
    Manual("manual"),
    Tagged("blocklistedTags"),
}

/**
 * One blocklisted title. [title] is the hydrated one, or the one the blocker gave when the
 * lookup failed; [tags] are the labels a tag rule blocked it under, empty for a manual block.
 * [tmdbId] keys the removal, since the server's DELETE is keyed that way rather than by [id].
 */
data class BlocklistItem(
    val id: Int,
    val tmdbId: Int,
    val mediaType: RequestMediaType,
    val title: String?,
    val posterUrl: String?,
    val year: String?,
    val addedBy: String?,
    val addedAtMillis: Long?,
    val tags: List<String>,
)

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
        /** Seerr 3.0+: the manual and tagged chips; Jellyseerr 2.x has one list. */
        val hasFilters: Boolean,
        val canManage: Boolean,
        val canBlockCollections: Boolean,
        val actingTmdbIds: Set<Int>,
        /** The server's web root, for opening a title there. */
        val webRoot: String,
    ) : BlocklistUiState {
        val isFiltered: Boolean get() = filter != BlocklistFilter.All || search.isNotBlank()
    }
}

sealed interface BlocklistEvent {
    data object Removed : BlocklistEvent

    data class CollectionChanged(
        val blocked: Boolean,
    ) : BlocklistEvent

    data class Failed(
        val error: SeerrError,
    ) : BlocklistEvent
}
