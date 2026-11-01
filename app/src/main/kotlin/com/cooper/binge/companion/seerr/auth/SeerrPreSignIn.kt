package com.cooper.binge.companion.seerr.auth

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrPasswordResetBody
import com.cooper.binge.companion.seerr.seerr.isValidBaseUrl
import com.cooper.binge.companion.seerr.seerr.normaliseBaseUrl
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * The calls a server answers before sign-in: a Quick Connect code and its polling, and a password reset. They go through
 * a throwaway client with no credentials, so they have no use for [SeerrConnection]'s user and profile caches.
 */
internal class SeerrPreSignIn(
    private val apis: SeerrApiFactory,
    private val quickConnectPollInterval: Duration,
) {
    suspend fun startQuickConnect(rawBaseUrl: String): Result<SeerrQuickConnect> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return attempt { apis.anonymous(baseUrl) { api -> api.initiateQuickConnect() } }
            .map { SeerrQuickConnect(code = it.code, secret = it.secret) }
    }

    /** A 404 from the check is Jellyfin having expired the code, [QuickConnectExpiredException]. */
    suspend fun awaitQuickConnect(
        rawBaseUrl: String,
        session: SeerrQuickConnect,
    ): Result<Unit> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return attempt {
            apis.anonymous(baseUrl) { api ->
                while (!api.checkQuickConnectApproved(session.secret)) delay(quickConnectPollInterval)
            }
        }
    }

    suspend fun requestPasswordReset(
        rawBaseUrl: String,
        email: String,
    ): Result<Unit> {
        if (!rawBaseUrl.isValidBaseUrl()) return Result.failure(InvalidServerUrlException())
        val baseUrl = rawBaseUrl.normaliseBaseUrl()
        return attempt { apis.anonymous(baseUrl) { api -> api.requestPasswordReset(SeerrPasswordResetBody(email)) } }
    }
}
