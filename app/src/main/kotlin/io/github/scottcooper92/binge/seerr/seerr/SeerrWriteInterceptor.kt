package io.github.scottcooper92.binge.seerr.seerr

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reports every write the saved server accepted, and returns the response untouched.
 *
 * It exists so the title-status cache does not have to be invalidated at each call site. An approve
 * made on this app's requests page and one made through the exported service are the same thing to
 * the server, and both leave through this client — so one hook here cannot be forgotten the way a
 * dozen could. A read is a GET and reports nothing.
 */
class SeerrWriteInterceptor(
    private val onWrite: () -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (chain.request().method != "GET" && response.isSuccessful) onWrite()
        return response
    }
}
