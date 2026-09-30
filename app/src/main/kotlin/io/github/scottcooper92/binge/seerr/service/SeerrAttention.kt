package io.github.scottcooper92.binge.seerr.service

import com.binge.companion.contracts.request.v1.Attention
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import retrofit2.HttpException

/**
 * What waits on the signed-in user: requests to moderate, issues to handle — each counted only
 * where the user holds the permission and the server has the endpoint, so a plain requester is
 * asked for nothing. A 401 here is the contract's `needs_reconnect`: the server is connected and
 * the session is what broke, which only this app's sign-in can mend.
 */
internal suspend fun SeerrConnection.readAttention(): Attention =
    try {
        // Inside the handler: with nothing cached the first read is `auth/me`, and an expired
        // session fails there rather than at the counts, so a warm process is not the only one
        // that gets the reconnect flag.
        val permissions = authenticatedUser().toPermissions()
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
        if (e.toSeerrError() != SeerrError.Unauthorized) throw e
        Attention
            .newBuilder()
            .setPendingCount(0)
            .setNeedsReconnect(true)
            .build()
    }
