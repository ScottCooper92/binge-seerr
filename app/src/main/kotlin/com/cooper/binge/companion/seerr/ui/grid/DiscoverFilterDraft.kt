package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.saveable.Saver
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.RATING_SORT_VOTE_FLOOR
import com.cooper.binge.companion.seerr.discover.grid.carriedTo
import com.cooper.binge.companion.seerr.discover.grid.carriedToOtherKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What the filter sheet is editing: the order and the filters, applied together by Show results. */
@Serializable
internal data class DiscoverFilterDraft(
    val kind: TitleKind,
    val sort: DiscoverSort,
    val filters: DiscoverFilters,
) {
    /** The draft for [next] kind of title: what belongs to one kind alone goes, and the order keeps its meaning. */
    fun withKind(next: TitleKind): DiscoverFilterDraft =
        if (next == kind) this else DiscoverFilterDraft(next, sort.carriedTo(next), filters.carriedToOtherKind())

    /**
     * The draft with [next] as its order. Moving to an order by rating from one that is not sets the vote-count floor, so the Vote
     * count row shows it and the user can clear it; a floor the user cleared stays cleared until the order changes again.
     */
    fun withSort(next: DiscoverSort): DiscoverFilterDraft {
        val floored = next.isByRating && !sort.isByRating && filters.voteCountMin == null
        return copy(sort = next, filters = if (floored) filters.copy(voteCountMin = RATING_SORT_VOTE_FLOOR) else filters)
    }

    companion object {
        /** Saves a draft across a rotation as the JSON of itself, so no Android type is needed to hold it. */
        val Saver: Saver<DiscoverFilterDraft, String> =
            Saver(
                save = { Json.encodeToString(it) },
                restore = { Json.decodeFromString<DiscoverFilterDraft>(it) },
            )
    }
}
