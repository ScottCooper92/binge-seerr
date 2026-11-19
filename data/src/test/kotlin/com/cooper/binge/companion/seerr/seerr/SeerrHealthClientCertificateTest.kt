package com.cooper.binge.companion.seerr.seerr

import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealth
import com.cooper.binge.companion.seerr.auth.SeerrHealth
import com.cooper.binge.companion.seerr.auth.toSeerr
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.server.connection.ConnectionHealthMonitor
import com.cooper.homelab.core.server.connection.SeerrClassifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/** What the client-certificate interceptor says about a server that asks for one, ahead of the failure the health interceptor reports (#2156). */
class SeerrHealthClientCertificateTest {
    private val server = MockWebServer().apply { start() }
    private val said = mutableListOf<String>()

    @After
    fun tearDown() = server.close()

    /** Outside the interceptor under test, so it sees a failure after the interceptor has said what it has to say. */
    private val recordingFailures =
        Interceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                said += "failure"
                throw e
            }
        }

    private fun clientWith(vararg inner: Interceptor): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(recordingFailures)
            .addInterceptor(SeerrClientCertificateInterceptor { said += "certificate=$it" })
            .apply { inner.forEach(::addInterceptor) }
            .build()

    private fun OkHttpClient.callOnce() {
        runCatching { newCall(Request.Builder().url(server.url("/api/v1/request")).build()).execute().close() }
    }

    @Test
    fun `a handshake that asks for a client certificate is said before the failure is reported`() {
        clientWith(Interceptor { throw SSLProtocolException("TLSV1_ALERT_CERTIFICATE_REQUIRED") }).callOnce()

        assertEquals(listOf("certificate=true", "failure"), said)
    }

    @Test
    fun `a reset connection is not a client certificate`() {
        clientWith(Interceptor { throw IOException("Connection reset") }).callOnce()

        assertEquals(listOf("certificate=false", "failure"), said)
    }

    @Test
    fun `an answer says there is no client certificate to ask for`() {
        server.enqueue(MockResponse(code = 200, body = "{}"))

        clientWith().callOnce()

        assertEquals(listOf("certificate=false"), said)
    }

    @Test
    fun `a call its caller cancelled leaves the reading as it was`() {
        clientWith(
            Interceptor { chain ->
                chain.call().cancel()
                throw SSLProtocolException("TLSV1_ALERT_CERTIFICATE_REQUIRED")
            },
        ).callOnce()

        assertEquals(listOf("failure"), said)
    }

    @Test
    fun `the health the saved client reports to reads a server that asks for a certificate as one that needs it`() {
        val id = ServerId("server-1")
        val health = SeerrHealth(ConnectionHealthMonitor(SeerrClassifier(::isArrProxyRoute), 1)) { id }
        val client =
            OkHttpClient
                .Builder()
                .apply { health.interceptors(id).forEach(::addInterceptor) }
                .addInterceptor(Interceptor { throw SSLProtocolException("TLSV1_ALERT_CERTIFICATE_REQUIRED") })
                .build()

        client.callOnce()

        val verdict = runBlocking { health.health.first().toSeerr(health.clientCertificateNeeded.first()) }
        assertEquals(SeerrConnectionHealth.ClientCertificateNeeded, verdict)
    }
}
