package com.cooper.binge.companion.seerr.seerr

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Says to [report] whether a call that failed to connect failed because the device does not trust the server's certificate, and
 * that an answered call did not. It is [SeerrClientCertificateInterceptor]'s counterpart for the other TLS refusal, and sits beside
 * it for the same reason: the shared library's health interceptor has no word for it. A call its caller gave up on says nothing.
 */
internal class SeerrCertificateRefusedInterceptor(
    private val report: (refused: Boolean) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response =
            try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                if (!chain.call().isCanceled()) report(e.certificateRefused())
                throw e
            }
        report(false)
        return response
    }
}
