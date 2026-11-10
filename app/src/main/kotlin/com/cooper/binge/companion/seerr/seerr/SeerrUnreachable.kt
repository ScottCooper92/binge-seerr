package com.cooper.binge.companion.seerr.seerr

import androidx.datastore.core.CorruptionException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * True where the server behind the companion did not answer: the connection failed, was reset or timed out, or a 5xx
 * says nothing behind a proxy did. These carry `SERVER_UNREACHABLE` (#1913). A 4xx, an unreadable body and a missing
 * session are not here: the server answered, or there is no session to ask it with. Nor is an `IOException` that is
 * [isTransportRefusal] or [isLocalFailure] (#1918): the contract keeps the detail for the server, not for us.
 */
fun Throwable.isServerUnreachable(): Boolean =
    (this is IOException && !isTransportRefusal() && !isLocalFailure()) || (this is HttpException && isServerFailure())

/** A certificate the platform will not trust, or cleartext its policy refuses: the server answered, or we declined to ask. */
internal fun Throwable.isTransportRefusal(): Boolean = this is SSLException || this is UnknownServiceException

/** A failure of the companion's own storage, which no retry of the server's can mend. */
internal fun Throwable.isLocalFailure(): Boolean = this is CorruptionException

internal fun HttpException.isServerFailure(): Boolean = code() >= HTTP_SERVER_ERROR_MIN

/** A connect, read or call timeout, which OkHttp reports as one of these two. */
internal fun IOException.isTimeout(): Boolean = this is SocketTimeoutException || this is InterruptedIOException
