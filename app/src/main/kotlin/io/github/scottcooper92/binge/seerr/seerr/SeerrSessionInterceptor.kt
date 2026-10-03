package io.github.scottcooper92.binge.seerr.seerr

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Set on a 403 that the server sent because the session is gone, not because of a permission. */
internal const val SESSION_REJECTED_HEADER = "X-Binge-Session-Rejected"

/**
 * Tells a dead session apart from a permission refusal on a 403, once, for every call this client makes
 * (#676). On every lineage `isAuthenticated()` answers 403 both when no user is attached and when the user
 * lacks the permission, so the code alone cannot say which. `auth/me` can: it needs no permission, so its
 * 403 means no user ([rejectsSession]). On a 403 anywhere else that does not name a quota, this asks
 * `auth/me` with the same credentials and, if that is refused too, marks the response with
 * [SESSION_REJECTED_HEADER]. The code stays 403; [toSeerrError] reads the mark as [SeerrError.Unauthorized],
 * so the Service, the screens and the notification poll all see a dead session as one.
 */
class SeerrSessionInterceptor(
    baseUrl: String,
) : Interceptor {
    private val authMe = baseUrl.toHttpUrl().resolve(AUTH_ME_PATH)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != HTTP_FORBIDDEN || request.url == authMe || authMe == null) return response
        // Buffered so the original can be returned after the probe: these bodies are a short JSON error.
        val body = response.body
        val bytes = body.bytes()
        val buffered = response.newBuilder().body(bytes.toResponseBody(body.contentType())).build()
        if (bytes.decodeToString().contains("quota", ignoreCase = true)) return buffered
        val rejected =
            chain
                .proceed(
                    request
                        .newBuilder()
                        .url(authMe)
                        .get()
                        .build(),
                ).use { probe ->
                    probe.code == HTTP_UNAUTHORIZED || probe.code == HTTP_FORBIDDEN
                }
        return if (rejected) buffered.newBuilder().header(SESSION_REJECTED_HEADER, "true").build() else buffered
    }

    private companion object {
        const val AUTH_ME_PATH = "api/v1/auth/me"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
