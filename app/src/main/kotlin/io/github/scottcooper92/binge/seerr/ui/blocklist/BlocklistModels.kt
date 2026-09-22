package io.github.scottcooper92.binge.seerr.ui.blocklist

import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import kotlinx.serialization.Serializable

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
 *
 * [Serializable] because it travels whole as a `BlocklistDetailRoute` navigation key: there is no
 * `GET` for a single blocklist entry, only the paged list this app already read it off of.
 */
@Serializable
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
        /** Bumped by a removal or a collection change; each filter's page refreshes once it trails this. */
        val listVersion: Int,
        /** Seerr 3.0+: the manual and tagged chips; Jellyseerr 2.x has one list. */
        val hasFilters: Boolean,
        val canManage: Boolean,
        val canBlockCollections: Boolean,
        val actingTmdbIds: Set<Int>,
    ) : BlocklistUiState {
        /** Per page, not per selection: an empty page says why it is empty, and a swiped-to page is its own. */
        fun isFiltered(filter: BlocklistFilter): Boolean = filter != BlocklistFilter.All || search.isNotBlank()
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
