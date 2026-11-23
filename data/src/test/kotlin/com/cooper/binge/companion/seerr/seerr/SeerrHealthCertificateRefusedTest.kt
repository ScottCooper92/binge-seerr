package com.cooper.binge.companion.seerr.seerr

import com.cooper.binge.companion.seerr.auth.SeerrAuthFailureClassifier
import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealth
import com.cooper.binge.companion.seerr.auth.SeerrHealth
import com.cooper.binge.companion.seerr.auth.toSeerr
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.server.connection.ConnectionHealth
import com.cooper.homelab.core.server.connection.ConnectionHealthMonitor
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
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/** What the certificate-refused interceptor says about a server whose certificate the device does not trust, and what follows. */
class SeerrHealthCertificateRefusedTest {
    private val server = MockWebServer().apply { start() }
    private val said = mutableListOf<String>()

    @After
    fun tearDown() = server.close()

    private val untrusted =
        SSLHandshakeException("no").apply { initCause(CertificateException("untrusted")) }

    private val recordingFailures =
        Interceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                said += "failure"
                throw e
            }
        }

    private fun clientWith(inner: Interceptor? = null): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(recordingFailures)
            .addInterceptor(SeerrCertificateRefusedInterceptor { said += "refused=$it" })
            .apply { inner?.let(::addInterceptor) }
            .build()

    private fun OkHttpClient.callOnce() {
        runCatching { newCall(Request.Builder().url(server.url("/api/v1/request")).build()).execute().close() }
    }

    @Test
    fun `a handshake that refuses the server's certificate is said before the failure is reported`() {
        clientWith(Interceptor { throw untrusted }).callOnce()

        assertEquals(listOf("refused=true", "failure"), said)
    }

    @Test
    fun `a reset connection is not a refused certificate`() {
        clientWith(Interceptor { throw IOException("Connection reset") }).callOnce()

        assertEquals(listOf("refused=false", "failure"), said)
    }

    @Test
    fun `an answer says the certificate was not refused`() {
        server.enqueue(MockResponse(code = 200, body = "{}"))

        clientWith().callOnce()

        assertEquals(listOf("refused=false"), said)
    }

    @Test
    fun `a call its caller cancelled leaves the reading as it was`() {
        clientWith(
            Interceptor { chain ->
                chain.call().cancel()
                throw untrusted
            },
        ).callOnce()

        assertEquals(listOf("failure"), said)
    }

    @Test
    fun `an unreachable server whose certificate was refused reads as that, not as unreachable`() {
        assertEquals(SeerrConnectionHealth.CertificateRefused, ConnectionHealth.Unreachable.toSeerr(false, true))
    }

    @Test
    fun `a refused certificate comes before a client certificate, which a handshake only asks for after it`() {
        assertEquals(SeerrConnectionHealth.CertificateRefused, ConnectionHealth.Unreachable.toSeerr(true, true))
    }

    @Test
    fun `a server that answered is not a refused certificate, whatever was reported before`() {
        assertEquals(SeerrConnectionHealth.Healthy, ConnectionHealth.Healthy.toSeerr(false, true))
    }

    @Test
    fun `the health the saved client reports to reads a refused certificate as one`() {
        val id = ServerId("server-1")
        val health = SeerrHealth(ConnectionHealthMonitor(SeerrAuthFailureClassifier(::isArrProxyRoute), 1)) { id }
        val client =
            OkHttpClient
                .Builder()
                .apply { health.interceptors(id).forEach(::addInterceptor) }
                .addInterceptor(Interceptor { throw untrusted })
                .build()

        client.callOnce()

        val verdict =
            runBlocking {
                health.health.first().toSeerr(health.clientCertificateNeeded.first(), health.certificateRefused.first())
            }
        assertEquals(SeerrConnectionHealth.CertificateRefused, verdict)
    }
}
