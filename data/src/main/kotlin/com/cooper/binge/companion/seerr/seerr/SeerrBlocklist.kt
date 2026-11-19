package com.cooper.binge.companion.seerr.seerr

import retrofit2.HttpException

/** Seerr's answer to a second add of a title already on the blocklist, where its database is SQLite. */
private const val HTTP_PRECONDITION_FAILED = 412

/**
 * Puts a title on the blocklist, reading "already on it" as done. Seerr refuses a second add: 412 "Item already
 * blocklisted" ("blacklisted" on Jellyseerr 2.x) where its database is SQLite, and 409 where it is Postgres, which
 * reports the same unique-key clash as a generic conflict. Either way the title is blocked, which is what was asked.
 * The one reading for the exported Service (#1000) and the console (#1139), so the two cannot disagree. [path] is
 * [SeerrServerProfile.blocklistPath].
 */
suspend fun SeerrApi.addToBlocklistOnce(
    path: String,
    body: SeerrAddToBlocklistBody,
) {
    try {
        addToBlocklist(path, body)
    } catch (e: HttpException) {
        if (e.code() != HTTP_PRECONDITION_FAILED && e.code() != HTTP_CONFLICT) throw e
    }
}

/**
 * Takes a title off the blocklist: the one place that makes the two server-dependent choices, the path
 * ([SeerrServerProfile.blocklistPath]) and whether to send the media type ([SeerrServerProfile.unblockMediaType]).
 * [mediaType] is `movie` or `tv`. The exported Service and the console's screens all unblock through it.
 */
suspend fun SeerrApi.unblockTitle(
    profile: SeerrServerProfile,
    tmdbId: Int,
    mediaType: String,
) = removeFromBlocklist(profile.blocklistPath, tmdbId, profile.unblockMediaType(mediaType))
