package com.cooper.binge.companion.seerr.seerr

import androidx.datastore.core.CorruptionException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownServiceException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

private const val HTTP_SERVER_ERROR_MIN = 500
private const val MAX_CAUSE_DEPTH = 8

/**
 * True where the server behind the companion did not answer: the connection failed, was reset or timed out, or a 5xx
 * says nothing behind a proxy did. These carry `SERVER_UNREACHABLE` (#1913). A 4xx, an unreadable body and a missing
 * session are not here: the server answered, or there is no session to ask it with. Nor is an `IOException` that is
 * [isTransportRefusal] or [isLocalFailure] (#1918): the contract keeps the detail for the server, not for us.
 */
fun Throwable.isServerUnreachable(): Boolean =
    (this is IOException && !isTransportRefusal() && !isLocalFailure()) || (this is HttpException && isServerFailure())

/**
 * A certificate the platform will not trust, or cleartext its policy refuses: the server answered, or we declined to ask.
 * An `SSLException` counts only when it is about the certificate: a hostname that does not match, or a cause that is a
 * [CertificateException] (Conscrypt's trust manager wraps an expired or untrusted one so). Any other `SSLException`,
 * including a connection the server dropped, which Conscrypt also reports that way, stays unreachable (#1933). That
 * deliberately includes a failed negotiation with a server that did answer, such as an `https://` address pointed at
 * a plain-HTTP port (`SSLProtocolException`) or a protocol-version alert: the user's fix is the address either way (#1936).
 */
internal fun Throwable.isTransportRefusal(): Boolean =
    this is UnknownServiceException || this is SSLPeerUnverifiedException || (this is SSLException && hasCertificateCause())

private fun Throwable.hasCertificateCause(): Boolean =
    generateSequence(cause) {
        it.cause
    }.take(MAX_CAUSE_DEPTH).any { it is CertificateException }

/**
 * A failure of the companion's own storage, which no retry of the server's can mend. Only [CorruptionException]: a disk
 * `IOException` from DataStore cannot be told from a socket one by its type, so it stays with the network's.
 */
internal fun Throwable.isLocalFailure(): Boolean = this is CorruptionException

internal fun HttpException.isServerFailure(): Boolean = code() >= HTTP_SERVER_ERROR_MIN

/** A connect, read or call timeout, which OkHttp reports as one of these two. */
internal fun IOException.isTimeout(): Boolean = this is SocketTimeoutException || this is InterruptedIOException
