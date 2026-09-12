package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthReporter
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * Reports every saved-server call's outcome to the health monitor and returns the response
 * untouched; it never alters or short-circuits a request. A 401 or 403 is the credentials; a
 * transport failure, any 5xx or a 429 (up, but rate-limiting us) is transient; every other answer,
 * a non-auth 4xx included, means the server reached us and accepted the credentials.
 */
class SeerrHealthInterceptor(
    private val reporter: SeerrConnectionHealthReporter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response =
            try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                reporter.reportNetworkFailure()
                throw e
            }
        when {
            response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN -> reporter.reportAuthFailure()
            response.code == HTTP_TOO_MANY_REQUESTS || response.code >= HTTP_SERVER_ERROR_MIN -> reporter.reportNetworkFailure()
            else -> reporter.reportSuccess()
        }
        return response
    }
}
