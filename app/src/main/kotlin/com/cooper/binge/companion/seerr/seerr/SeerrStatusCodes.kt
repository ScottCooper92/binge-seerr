package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.Serializable

/**
 * Seerr's `MediaStatus` enum, a plain int on the wire. Typed so it cannot be confused with
 * [SeerrRequestStatusCode], which shares the same raw range.
 *
 * Deleted is reached only on Jellyseerr and Seerr: Overseerr has no delete-files route and its
 * enum stops at Available.
 */
@JvmInline
@Serializable
value class SeerrMediaStatusCode(
    val raw: Int,
) {
    companion object {
        val Unknown = SeerrMediaStatusCode(1)
        val Pending = SeerrMediaStatusCode(2)
        val Processing = SeerrMediaStatusCode(3)
        val PartiallyAvailable = SeerrMediaStatusCode(4)
        val Available = SeerrMediaStatusCode(5)
        val Blocklisted = SeerrMediaStatusCode(6)
        val Deleted = SeerrMediaStatusCode(7)
    }
}

/**
 * Seerr's `MediaRequestStatus` enum. It runs past Declined: an auto-approved request that finishes
 * downloading lands on Completed (5), and a fulfilment that errors lands on Failed (4).
 */
@JvmInline
@Serializable
value class SeerrRequestStatusCode(
    val raw: Int,
) {
    companion object {
        val Pending = SeerrRequestStatusCode(1)
        val Approved = SeerrRequestStatusCode(2)
        val Declined = SeerrRequestStatusCode(3)
        val Failed = SeerrRequestStatusCode(4)
        val Completed = SeerrRequestStatusCode(5)
    }
}

/** Seerr's `IssueType` enum: 1 video, 2 audio, 3 subtitles, 4 other. */
@JvmInline
@Serializable
value class SeerrIssueTypeCode(
    val raw: Int,
) {
    companion object {
        val Video = SeerrIssueTypeCode(1)
        val Audio = SeerrIssueTypeCode(2)
        val Subtitles = SeerrIssueTypeCode(3)
        val Other = SeerrIssueTypeCode(4)
    }
}
