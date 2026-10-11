package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.Attention
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.rejectsSession
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toSeerrError
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
        // A count refused with the user served from cache is a permission or a session that expired since.
        // The client has already asked `auth/me` which (SeerrSessionInterceptor), so only the session
        // reads as Unauthorized here (#673, #676).
        if (e.toSeerrError() == SeerrError.Unauthorized) RECONNECT else throw e
    }
}

private val RECONNECT: Attention =
    Attention
        .newBuilder()
        .setPendingCount(0)
        .setNeedsReconnect(true)
        .build()
