package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.saveable.Saver
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the filter sheet is editing: the order and the filters, applied together by Show results. */
@Serializable
internal data class DiscoverFilterDraft(
    val sort: DiscoverSort,
    val filters: DiscoverFilters,
) {
    companion object {
        /** Saves a draft across a rotation as the JSON of itself, so no Android type is needed to hold it. */
        val Saver: Saver<DiscoverFilterDraft, String> =
            Saver(
                save = { Json.encodeToString(serializer(), it) },
                restore = { Json.decodeFromString(serializer(), it) },
            )
    }
}
