package com.cooper.binge.companion.seerr.seerr

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Says to [report] whether a call that failed to connect failed because the server asked for a client certificate, and that an
 * answered call did not. The shared library's health interceptor reports the call itself and has no word for this, so it is this
 * app's. It sits inside that one, so what it says is already known when the failure's verdict is read (#2156). A call its caller
 * gave up on says nothing: the reading stays as it was.
 */
internal class SeerrClientCertificateInterceptor(
    private val report: (needed: Boolean) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response =
            try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                if (!chain.call().isCanceled()) report(e.wantsClientCertificate())
                throw e
            }
        report(false)
        return response
    }
}
