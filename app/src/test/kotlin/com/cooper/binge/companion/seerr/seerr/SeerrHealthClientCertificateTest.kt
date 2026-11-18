package com.cooper.binge.companion.seerr.seerr

import com.cooper.binge.companion.seerr.auth.SeerrHealthReporter
import com.cooper.homelab.core.server.connection.CallResult
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLProtocolException

/** What the health interceptor says about a server that asks for a client certificate, ahead of the failure it reports (#2156). */
class SeerrHealthClientCertificateTest {
    private class Recording : SeerrHealthReporter {
        val said = mutableListOf<String>()

        override fun report(result: CallResult) {
            said += if (result is CallResult.TransportFailure) "failure cancelled=${result.cancelled}" else "answered"
        }

        override fun reportClientCertificate(needed: Boolean) {
            said += "certificate=$needed"
        }
    }

    private val server = MockWebServer().apply { start() }
    private val reporter = Recording()

    @After
    fun tearDown() = server.close()

    private fun failingWith(failure: () -> IOException): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(SeerrHealthInterceptor(reporter))
            .addInterceptor(Interceptor { throw failure() })
            .build()

    private fun OkHttpClient.callOnce() {
        runCatching { newCall(Request.Builder().url(server.url("/api/v1/request")).build()).execute().close() }
    }

    @Test
    fun `a handshake that asks for a client certificate is said before the failure is reported`() {
        failingWith { SSLProtocolException("TLSV1_ALERT_CERTIFICATE_REQUIRED") }.callOnce()

        assertEquals(listOf("certificate=true", "failure cancelled=false"), reporter.said)
    }

    @Test
    fun `a reset connection is not a client certificate`() {
        failingWith { IOException("Connection reset") }.callOnce()

        assertEquals(listOf("certificate=false", "failure cancelled=false"), reporter.said)
    }

    @Test
    fun `an answer says there is no client certificate to ask for`() {
        server.enqueue(MockResponse(code = 200, body = "{}"))
        OkHttpClient
            .Builder()
            .addInterceptor(SeerrHealthInterceptor(reporter))
            .build()
            .callOnce()

        assertEquals(listOf("certificate=false", "answered"), reporter.said)
    }

    @Test
    fun `a call its caller cancelled leaves the reading as it was`() {
        val cancelling =
            OkHttpClient
                .Builder()
                .addInterceptor(SeerrHealthInterceptor(reporter))
                .addInterceptor(
                    Interceptor { chain ->
                        chain.call().cancel()
                        throw SSLProtocolException("TLSV1_ALERT_CERTIFICATE_REQUIRED")
                    },
                ).build()

        cancelling.callOnce()

        assertEquals(listOf("failure cancelled=true"), reporter.said)
    }
}
