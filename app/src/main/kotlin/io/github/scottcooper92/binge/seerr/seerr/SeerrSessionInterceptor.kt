package io.github.scottcooper92.binge.seerr.seerr

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Set on a 403 that the server sent because the session is gone, not because of a permission. */
internal const val SESSION_REJECTED_HEADER = "X-Binge-Session-Rejected"

private const val AUTH_ME_PATH = "api/v1/auth/me"
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

/** `GET /auth/me` on the server at [baseUrl]: the one call whose 403 means no user ([rejectsSession]). */
internal fun authMeUrl(baseUrl: String): HttpUrl? = baseUrl.toHttpUrl().resolve(AUTH_ME_PATH)

/**
 * Whether an answer means the session is gone, not a permission: a 401, or a 403 [SeerrSessionInterceptor]
 * marked after `auth/me` refused the same credentials. [toSeerrError] reads it as [SeerrError.Unauthorized],
 * and the health monitor and telemetry read the same rule, so none of them treats a plain 403 as a dead
 * session (#689). A 403 from `auth/me` itself is never marked; a caller that sees one applies [rejectsSession].
 */
internal fun isSessionRejection(
    code: Int,
    headers: Headers,
): Boolean = code == HTTP_UNAUTHORIZED || headers[SESSION_REJECTED_HEADER] != null

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
    private val authMe = authMeUrl(baseUrl)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val authMe = authMe
        if (response.code != HTTP_FORBIDDEN || request.url == authMe || authMe == null) return response
        // Buffered so the original can be returned after the probe: these bodies are a short JSON error.
        // The probe is an extra opinion on a response already held, so losing the body or the probe
        // returns the 403 unmarked rather than a transport failure ([mentionsQuota] reads a body the same way).
        val body = response.body
        val bytes = runCatching { body.bytes() }.getOrDefault(ByteArray(0))
        val buffered = response.newBuilder().body(bytes.toResponseBody(body.contentType())).build()
        if (bytes.decodeToString().namesQuota()) return buffered
        val rejected = runCatching { rejectsSession(chain, request, authMe) }.getOrDefault(false)
        return if (rejected) buffered.newBuilder().header(SESSION_REJECTED_HEADER, "true").build() else buffered
    }

    private fun rejectsSession(
        chain: Interceptor.Chain,
        request: Request,
        authMe: HttpUrl,
    ): Boolean =
        chain
            .proceed(
                request
                    .newBuilder()
                    .url(authMe)
                    .get()
                    .build(),
            ).use { probe -> probe.code == HTTP_UNAUTHORIZED || probe.code == HTTP_FORBIDDEN }
}
