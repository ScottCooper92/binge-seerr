package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthReporter
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * Reports every saved-server call's outcome to the health monitor and returns the response
 * untouched; it never alters or short-circuits a request.
 *
 * The credentials are at fault only when the session is gone: a 401, a 403 that [SeerrSessionInterceptor]
 * marked ([isSessionRejection], the rule [toSeerrError] reads too), or a 403 from `auth/me` itself
 * ([rejectsSession]). Any other 403 is a permission, such as a user without `MANAGE_ISSUES` opening the
 * issues list. A transport failure, any 5xx or a 429 (up, but rate-limiting us) is transient. Every other
 * answer, a permission 403 and any other 4xx included, means the server reached us and accepted the
 * credentials.
 *
 * It sits outside [SeerrSessionInterceptor], so the response it reads already carries the mark.
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
            isSessionRejection(response.code, response.headers) -> reporter.reportAuthFailure()
            response.code == HTTP_FORBIDDEN && request.url == authMe -> reporter.reportAuthFailure()
            response.code == HTTP_TOO_MANY_REQUESTS || response.code >= HTTP_SERVER_ERROR_MIN -> reporter.reportNetworkFailure()
            else -> reporter.reportSuccess()
        }
        return response
    }
}
