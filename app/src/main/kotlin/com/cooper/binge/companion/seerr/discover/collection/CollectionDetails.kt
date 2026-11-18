package com.cooper.binge.companion.seerr.discover.collection

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleResult

/** A collection as its page shows it: the name and story, and every film in it, oldest first. */
data class CollectionDetails(
    val id: Int,
    val name: String,
    val overview: String?,
    val backdropUrl: String?,
    val parts: List<TitleResult>,
) {
    /** The server has no collection flag to read: a collection is blocked when every film in it is. */
    val blocked: Boolean get() = parts.isNotEmpty() && parts.all { it.availability == TitleAvailability.Blocklisted }

    /** The films nothing has been asked for or added of yet: the ones the page can still offer to request. */
    val toRequest: Int get() = parts.count { it.availability == TitleAvailability.None }
}

/** Reads a collection from the connected server, and blocks or unblocks it for a viewer who may. */
interface CollectionRepository {
    suspend fun collection(id: Int): CollectionDetails

    /** Whether the viewer may block collections here: the server must have the endpoint and the account must manage the blocklist. */
    suspend fun mayBlock(): Boolean

    suspend fun setBlocked(
        id: Int,
        blocked: Boolean,
    )
}
