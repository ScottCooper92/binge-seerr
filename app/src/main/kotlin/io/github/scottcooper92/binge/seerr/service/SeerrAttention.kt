package io.github.scottcooper92.binge.seerr.service

import com.binge.companion.contracts.request.v1.Attention
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.rejectsSession
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import retrofit2.HttpException

/**
 * What waits on the signed-in user: requests to moderate, issues to handle — each counted only
 * where the user holds the permission and the server has the endpoint, so a plain requester is
 * asked for nothing. A session `auth/me` refuses ([rejectsSession]) is the contract's `needs_reconnect`:
 * the server is connected and the session is what broke, which only this app's sign-in can mend.
 */
internal suspend fun SeerrConnection.readAttention(): Attention {
    val permissions =
        try {
            authenticatedUser().toPermissions()
        } catch (e: HttpException) {
            if (e.toSeerrError().rejectsSession) return RECONNECT
            throw e
        }
    return try {
        val profile = profile()
        val api = api()
        val pending = if (permissions.canManageRequests) api.requestCount().pending else 0
        val issues = if (permissions.canManageIssues && profile.hasCounts) api.issueCount().open else 0
        Attention
            .newBuilder()
            .setPendingCount(pending + issues)
            .setNeedsReconnect(false)
            .build()
    } catch (e: HttpException) {
        // A count refused with the user served from cache is either a permission or a session that expired
        // since: every lineage answers both with 403 there. Only `auth/me` tells them apart, so it is read
        // again rather than trusting the cached copy (#672).
        if (e.toSeerrError().rejectsSession && sessionRejected()) RECONNECT else throw e
    }
}

/** Re-reads `auth/me`: true when the server refuses the session, false when it still answers for the user. */
private suspend fun SeerrConnection.sessionRejected(): Boolean =
    try {
        refreshAuthenticatedUser()
        false
    } catch (e: HttpException) {
        if (e.toSeerrError().rejectsSession) true else throw e
    }

private val RECONNECT: Attention =
    Attention
        .newBuilder()
        .setPendingCount(0)
        .setNeedsReconnect(true)
        .build()
