package com.cooper.binge.companion.seerr.seerr

import okhttp3.HttpUrl

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
