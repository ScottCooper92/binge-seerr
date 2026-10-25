package com.cooper.binge.companion.seerr.seerr

/** Where a request was sent: the *arr kind and its server, and the quality profile on it. Either id is null when the server did not say. */
data class SeerrDestination(
    val isTv: Boolean,
    val serverId: Int?,
    val profileId: Int?,
)

/** The quality profile name for a request's destination, or null where it is not known. Display only; the contract never carries an id. */
fun interface QualityLabels {
    fun labelOf(destination: SeerrDestination): String?

    companion object {
        val None = QualityLabels { null }
    }
}

fun SeerrRequestDto.destination(): SeerrDestination = SeerrDestination(isTv, serverId, profileId)

/** [isTv] is the title's, since a request listed under a title's media info does not repeat its type. */
fun SeerrRequestSummaryDto.destination(isTv: Boolean): SeerrDestination = SeerrDestination(isTv, serverId, profileId)
