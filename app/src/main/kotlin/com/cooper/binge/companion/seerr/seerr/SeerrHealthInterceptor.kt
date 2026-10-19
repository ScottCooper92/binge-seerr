package com.cooper.binge.companion.seerr.seerr

import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealthReporter
import com.cooper.binge.companion.seerr.demo.isDemo
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * Reports every saved-server call's outcome to the health monitor and returns the response untouched.
 *
 * The credentials are at fault only when the session is gone: a 401 `auth/me` agrees with, a 403
 * [SeerrSessionInterceptor] marked ([isSessionRejection]), or a 403 from `auth/me` itself ([rejectsSession]).
 * A 401 `auth/me` contradicts is a refusal (#1165). A transport failure, any 5xx or a 429 is transient. Every
 * other answer, a permission 403 included, means the server reached us and accepted the credentials.
 * The demo is always reached: its 501 is an answer, not an outage. This sits outside [SeerrSessionInterceptor].
 */
class SeerrHealthInterceptor(
    private val reporter: SeerrConnectionHealthReporter,
    baseUrl: String,
) : Interceptor {
    private val authMe = authMeUrl(baseUrl)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response =
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                reporter.reportNetworkFailure()
                throw e
            }
        when {
            request.url.isDemo() -> reporter.reportSuccess()
            isSessionRejection(response.code, response.headers) -> reporter.reportAuthFailure()
            response.code == HTTP_FORBIDDEN && request.url == authMe -> reporter.reportAuthFailure()
            response.code == HTTP_TOO_MANY_REQUESTS || response.code >= HTTP_SERVER_ERROR_MIN -> reporter.reportNetworkFailure()
            else -> reporter.reportSuccess()
        }
        return response
    }
}
