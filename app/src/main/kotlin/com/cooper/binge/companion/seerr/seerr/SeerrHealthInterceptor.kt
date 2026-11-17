package com.cooper.binge.companion.seerr.seerr

import com.cooper.binge.companion.seerr.auth.SeerrHealthReporter
import com.cooper.binge.companion.seerr.demo.isDemo
import com.cooper.homelab.core.server.connection.CallResult
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val HTTP_OK = 200

/**
 * Reports every saved-server call to the health monitor and returns the response untouched. What a call means is the shared
 * library's [com.cooper.homelab.core.server.connection.SeerrClassifier]; this builds the [CallResult] from the exchange.
 * The credentials are at fault only when the session is gone ([isSessionRejection], 403 from `auth/me` included; #1165).
 * The demo is always reached: its 501 is an answer, so it is reported as one. This sits outside [SeerrSessionInterceptor].
 */
class SeerrHealthInterceptor(
    private val reporter: SeerrHealthReporter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response =
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                reporter.report(CallResult.TransportFailure())
                throw e
            }
        reporter.report(
            if (request.url.isDemo()) {
                CallResult.Answered(HTTP_OK)
            } else {
                CallResult.Answered(
                    status = response.code,
                    path = request.url.encodedPathSegments,
                    sessionRejected = isSessionRejection(response.code, response.headers),
                )
            },
        )
        return response
    }
}

/**
 * Whether Seerr answers this route by calling a Radarr or Sonarr instance live, from `server/routes/service.ts`
 * and `settings/{radarr,sonarr}.ts` upstream: `service/{radarr,sonarr}/{id}`, `service/sonarr/lookup/{tmdbId}`,
 * `settings/{radarr,sonarr}/test` and `settings/radarr/{id}/profiles`. They answer 500 when the instance is down.
 * Matched after `api/v1`, so a base URL with a path prefix still matches.
 */
internal fun HttpUrl.isArrProxyRoute(): Boolean = isArrProxyRoute(encodedPathSegments)

/** [isArrProxyRoute] on a path already split into segments, which is what the shared classifier is handed. */
internal fun isArrProxyRoute(segments: List<String>): Boolean {
    val api = segments.indexOf("v1") + 1
    val route = segments.drop(api)
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
