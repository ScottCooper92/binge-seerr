package com.cooper.binge.companion.seerr.seerr

import androidx.datastore.core.CorruptionException
import com.cooper.binge.companion.seerr.auth.NotConnectedException
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.server.network.LocalNetworkDeniedException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/** The shared library's failure for each answer `toSeerrError` classifies, so a screen can move over without its behaviour changing. */
class SeerrServerFailureTest {
    /** `Refused` compares by type alone, so a test reads the reason itself. */
    private fun ServerFailure.reason(): String? = (this as ServerFailure.Refused).reason

    private fun http(
        code: Int,
        body: String = "{}",
    ): HttpException = HttpException(Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `a 401 with no live session is the session rejected`() =
        runTest {
            assertEquals(ServerFailure.SessionRejected, http(401).toServerFailure())
            assertTrue(http(401).toServerFailure().rejectsSession)
        }

    @Test
    fun `a 403 that names no quota is a permission and a quota 403 is a refusal`() =
        runTest {
            assertEquals(ServerFailure.NotPermitted, http(403, """{"message":"No permission"}""").toServerFailure())
            assertEquals("Quota exceeded", http(403, """{"message":"Quota exceeded"}""").toServerFailure().reason())
            assertFalse(http(403, """{"message":"Quota exceeded"}""").toServerFailure().rejectsSession)
        }

    @Test
    fun `a 404 is gone a 5xx is the server and another 4xx is a refusal with the server's line`() =
        runTest {
            assertEquals(ServerFailure.NotFound, http(404).toServerFailure())
            assertEquals(ServerFailure.ServerError, http(502).toServerFailure())
            assertEquals("read-only", http(400, """{"message":" read-only "}""").toServerFailure().reason())
            assertNull(http(409, "{}").toServerFailure().reason())
        }

    @Test
    fun `a failure with no connection or no network reads as the library does`() =
        runTest {
            assertEquals(ServerFailure.NotConnected, NotConnectedException().toServerFailure())
            assertEquals(ServerFailure.LocalNetworkDenied, LocalNetworkDeniedException("192.168.1.2").toServerFailure())
            assertTrue((SocketTimeoutException().toServerFailure() as ServerFailure.Unreachable).timedOut)
            assertFalse((UnknownHostException("nas").toServerFailure() as ServerFailure.Unreachable).timedOut)
            assertFalse((IOException("reset").toServerFailure() as ServerFailure.Unreachable).timedOut)
        }

    @Test
    fun `a certificate refusal is its own failure, and an unreadable body and a corrupt store are not unreachable`() =
        runTest {
            val cert = SSLHandshakeException("no").apply { initCause(CertificateException("untrusted")) }
            assertEquals(ServerFailure.CertificateRefused, cert.toServerFailure())
            assertEquals(ServerFailure.Unreadable, SerializationException("body").toServerFailure())
            assertEquals(ServerFailure.Unknown, CorruptionException("store").toServerFailure())
            assertEquals(ServerFailure.Unknown, IllegalStateException().toServerFailure())
        }

    @Test
    fun `only the session and the permission of a refused auth me count as a rejected session`() =
        runTest {
            val rejects = listOf(http(401), http(403)).map { it.toServerFailure().rejectsSession }
            assertEquals(listOf(true, true), rejects)
            assertFalse(http(500).toServerFailure().rejectsSession)
            assertFalse(IOException().toServerFailure().rejectsSession)
        }
}
