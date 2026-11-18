package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.HTTP_NOT_FOUND
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import retrofit2.HttpException

/** A Quick Connect session: the [code] the user approves on Jellyfin, the [secret] this app polls with. */
data class SeerrQuickConnect(
    val code: String,
    val secret: String,
)

/** The Quick Connect code was not approved on the Jellyfin server before it expired. */
class QuickConnectExpiredException : IllegalStateException("The Quick Connect code expired before it was approved")

internal suspend fun SeerrApi.checkQuickConnectApproved(secret: String): Boolean =
    try {
        checkQuickConnect(secret).authenticated
    } catch (e: HttpException) {
        if (e.code() == HTTP_NOT_FOUND) throw QuickConnectExpiredException() else throw e
    }
