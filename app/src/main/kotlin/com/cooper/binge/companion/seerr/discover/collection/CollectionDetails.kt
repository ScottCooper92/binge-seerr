package com.cooper.binge.companion.seerr.discover.collection

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleResult

/** A collection as its page shows it: the name and story, and every film in it, oldest first. */
data class CollectionDetails(
    val id: Int,
    val name: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val parts: List<TitleResult>,
) {
    /** The films nothing has been asked for or added of yet: the ones the page can still offer to request. */
    val toRequest: Int get() = parts.count { it.availability == TitleAvailability.None }
}

/** Reads a collection from the connected server. */
interface CollectionRepository {
    suspend fun collection(id: Int): CollectionDetails

    /** Whether the viewer may request films: the Request button is not drawn for one who may not. */
    suspend fun mayRequest(): Boolean
}
