package com.cooper.binge.companion.seerr.seerr

import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealthReporter
import com.cooper.binge.companion.seerr.demo.isDemo
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_INTERNAL_SERVER_ERROR = 500
private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * Reports every saved-server call's outcome to the health monitor and returns the response untouched.
 *
 * The credentials are at fault only when the session is gone: a 401 `auth/me` agrees with, or a 403
 * [SeerrSessionInterceptor] marked ([isSessionRejection]), a 403 from `auth/me` itself included.
 * A 401 `auth/me` contradicts is a refusal (#1165). A transport failure, a 429 or a 5xx (bar an [isArrProxyRoute] 500) is transient.
 * Every other answer, a permission 403 included, means the server reached us and accepted the credentials. The demo
 * is always reached: its 501 is an answer, not an outage. This sits outside [SeerrSessionInterceptor].
 */
class SeerrHealthInterceptor(
    private val reporter: SeerrConnectionHealthReporter,
) : Interceptor {
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
            response.code == HTTP_INTERNAL_SERVER_ERROR && request.url.isArrProxyRoute() -> reporter.reportSuccess()
            response.code == HTTP_TOO_MANY_REQUESTS || response.code >= HTTP_SERVER_ERROR_MIN -> reporter.reportNetworkFailure()
            else -> reporter.reportSuccess()
        }
        return response
    }
}

/**
 * Whether Seerr answers this route by calling a Radarr or Sonarr instance live, from `server/routes/service.ts`
 * and `settings/{radarr,sonarr}.ts` upstream: `service/{radarr,sonarr}/{id}`, `service/sonarr/lookup/{tmdbId}`,
 * `settings/{radarr,sonarr}/test` and `settings/radarr/{id}/profiles`. They answer 500 when the instance is down.
 * Matched after `api/v1`, so a base URL with a path prefix still matches.
 */
internal fun HttpUrl.isArrProxyRoute(): Boolean {
    val api = encodedPathSegments.indexOf("v1") + 1
    val route = encodedPathSegments.drop(api)
    val (area, arr) = route.getOrNull(0) to route.getOrNull(1)
    if (api == 0 || arr !in ARR_SERVICES) return false
    return when (area) {
        "service" -> route.size > 2
        "settings" -> route.lastOrNull() in SETTINGS_ARR_CALLS
        else -> false
    }
}

private val ARR_SERVICES = setOf("radarr", "sonarr")
private val SETTINGS_ARR_CALLS = setOf("test", "profiles")
