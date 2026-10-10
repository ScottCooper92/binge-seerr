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

/** Set on a 401 that the server sent although `auth/me` still answers to the same credentials: a refusal, not the session (#997). */
internal const val SESSION_ALIVE_HEADER = "X-Binge-Session-Alive"

private const val AUTH_ME_PATH = "api/v1/auth/me"

/** `GET /auth/me` on the server at [baseUrl]: the one call whose 403 means no user ([rejectsSession]). */
internal fun authMeUrl(baseUrl: String): HttpUrl? = baseUrl.toHttpUrl().resolve(AUTH_ME_PATH)

/**
 * Whether an answer means the session is gone, not a permission: a 401 [SeerrSessionInterceptor] did not find the
 * session alive behind, or a 403 it marked after `auth/me` refused the same credentials. [toSeerrError] reads it as
 * [SeerrError.Unauthorized], and the health monitor and telemetry read the same rule, so none of them treats a plain
 * 403 as a dead session (#689), nor a 401 that Seerr meant as a refusal (#997). A 403 from `auth/me` itself is never
 * marked; a caller that sees one applies [rejectsSession]. A client without the interceptor, such as setup's probe of
 * candidate credentials, marks nothing, so its 401 is the credentials, as before.
 */
internal fun isSessionRejection(
    code: Int,
    headers: Headers,
): Boolean = (code == HTTP_UNAUTHORIZED && headers[SESSION_ALIVE_HEADER] == null) || headers[SESSION_REJECTED_HEADER] != null

/** A 401 Seerr sent while the session still answers: a refusal on the merits, which the Service reads per route (#997, #998). */
internal fun isLiveSessionRefusal(
    code: Int,
    headers: Headers,
): Boolean = code == HTTP_UNAUTHORIZED && headers[SESSION_ALIVE_HEADER] != null

/**
 * Tells a dead session apart from a permission refusal on a 403, once, for every call this client makes
 * (#676). On every lineage `isAuthenticated()` answers 403 both when no user is attached and when the user
 * lacks the permission, so the code alone cannot say which. `auth/me` can: it needs no permission, so its
 * 403 means no user ([rejectsSession]). On a 403 anywhere else that does not name a quota, this asks
 * `auth/me` with the same credentials and, if that is refused too, marks the response with
 * [SESSION_REJECTED_HEADER]. The code stays 403; [toSeerrError] reads the mark as [SeerrError.Unauthorized],
 * so the Service, the screens and the notification poll all see a dead session as one.
 *
 * A 401 is asked about the same way (#997). Seerr answers 401 on a few routes when the user is signed in and
 * the answer is no: deleting a request that is not theirs or no longer pending, and Jellyseerr 2.x's
 * blacklist delete of a title it does not hold (#998). If `auth/me` still answers, the 401 is marked with
 * [SESSION_ALIVE_HEADER], and nothing reads it as a dead session.
 */
class SeerrSessionInterceptor(
    baseUrl: String,
) : Interceptor {
    private val authMe = authMeUrl(baseUrl)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val authMe = authMe
        if (request.url == authMe || authMe == null) return response
        return when (response.code) {
            HTTP_UNAUTHORIZED -> checkUnauthorized(chain, request, response, authMe)
            HTTP_FORBIDDEN -> checkForbidden(chain, request, response, authMe)
            else -> response
        }
    }

    private fun checkForbidden(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
        authMe: HttpUrl,
    ): Response {
        // Buffered so the original can be returned after the probe: these bodies are a short JSON error.
        // The probe is an extra opinion on a response already held, so losing the body or the probe
        // returns the 403 unmarked rather than a transport failure ([mentionsQuota] reads a body the same way).
        val buffered = response.buffered()
        if (buffered.peekBody(Long.MAX_VALUE).string().namesQuota()) return buffered
        val rejected = attempt { rejectsSession(chain, request, authMe) }.getOrDefault(false)
        return if (rejected) buffered.newBuilder().header(SESSION_REJECTED_HEADER, "true").build() else buffered
    }

    /**
     * A 401 is marked alive only when `auth/me` answers with the user. One it refuses, or a probe that fails any other way,
     * leaves the 401 reading as the session, as it always did: a dead session must never pass for a refusal.
     */
    private fun checkUnauthorized(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
        authMe: HttpUrl,
    ): Response {
        val buffered = response.buffered()
        val alive = attempt { probe(chain, request, authMe) { it.isSuccessful } }.getOrDefault(false)
        return if (alive) buffered.newBuilder().header(SESSION_ALIVE_HEADER, "true").build() else buffered
    }

    private fun rejectsSession(
        chain: Interceptor.Chain,
        request: Request,
        authMe: HttpUrl,
    ): Boolean = probe(chain, request, authMe) { it.code == HTTP_UNAUTHORIZED || it.code == HTTP_FORBIDDEN }

    /** Asks `auth/me` with [request]'s own credentials and reads the answer with [read]. */
    private fun probe(
        chain: Interceptor.Chain,
        request: Request,
        authMe: HttpUrl,
        read: (Response) -> Boolean,
    ): Boolean =
        chain
            .proceed(
                request
                    .newBuilder()
                    .url(authMe)
                    .get()
                    .build(),
            ).use(read)
}

/** [this] with its body read into memory, or emptied if it will not read, so it can be returned after a probe. */
private fun Response.buffered(): Response {
    val bytes = attempt { body.bytes() }.getOrDefault(ByteArray(0))
    return newBuilder().body(bytes.toResponseBody(body.contentType())).build()
}
