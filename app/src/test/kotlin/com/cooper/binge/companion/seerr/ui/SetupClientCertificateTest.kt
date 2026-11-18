package com.cooper.binge.companion.seerr.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLProtocolException

class SetupClientCertificateTest {
    /** The alert the issue reports from a device, as text: the platform's wording around `TLSV1_ALERT_CERTIFICATE_REQUIRED`. */
    private val deviceAlert = "Read error: Failure in SSL library, usually a protocol error: TLSV1_ALERT_CERTIFICATE_REQUIRED"

    @Test
    fun `a server that asks for a client certificate is its own setup error`() {
        assertEquals(SetupError.ClientCertificateNeeded, SSLProtocolException(deviceAlert).toSetupError())
    }

    @Test
    fun `the alert is found where the platform wraps it`() {
        val wrapped =
            SSLHandshakeException(
                "handshake failed",
            ).apply { initCause(SSLProtocolException("TLSV1_ALERT_CERTIFICATE_REQUIRED")) }

        assertEquals(SetupError.ClientCertificateNeeded, wrapped.toSetupError())
    }

    @Test
    fun `a connection the server dropped stays unreachable`() {
        assertEquals(SetupError.Unreachable, SSLException("Connection reset by peer").toSetupError())
        assertEquals(SetupError.Unreachable, SocketTimeoutException("timeout").toSetupError())
    }

    @Test
    fun `a certificate the platform will not trust is not read as a client certificate`() {
        val untrusted = SSLHandshakeException("Chain validation failed").apply { initCause(CertificateException("expired")) }

        assertEquals(SetupError.Unknown, untrusted.toSetupError())
    }
}
