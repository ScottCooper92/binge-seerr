package com.cooper.binge.companion.seerr.seerr

import androidx.datastore.core.CorruptionException
import com.cooper.binge.companion.seerr.auth.NotConnectedException
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.server.network.FailureClassifier
import com.cooper.homelab.core.server.network.FailureMapper
import com.cooper.homelab.core.server.network.JvmTransportFailures
import com.cooper.homelab.core.server.network.RequestFacts
import com.cooper.homelab.core.server.network.ResponseBodyPeek
import com.cooper.homelab.core.server.network.TransportFailureReader
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * The failure of a call as the shared library names it, read by the same rules as [toSeerrError]: a 401 the session was not
 * found alive behind, or a 403 the session interceptor confirmed, is `SessionRejected`; a 401 with a live session and a
 * 403 that names no quota are `NotPermitted`; a quota 403 and any other 4xx are `Refused` with the server's own line; the
 * rest is the library's default.
 *
 * Suspends because the library reads a body through a suspend peek. A screen moves to this one at a time; until it has,
 * it keeps [toSeerrError].
 */
suspend fun Throwable.toServerFailure(): ServerFailure =
    when (this) {
        is HttpException -> FailureMapper(SeerrTransport, SeerrAnswer(this)).fromStatus(code(), ResponseBodyPeek { peekedBody() }, FACTS)
        else -> FailureMapper(SeerrTransport).fromThrowable(this)
    }

/**
 * The failure a shared list or loader hands back, as the screen's own error. A refusal is a rejection: the lists that use this only
 * read, so no quota applies. What the app has no word for reads as unknown.
 */
fun ServerFailure.toSeerrError(): SeerrError =
    when (this) {
        ServerFailure.NotConnected -> SeerrError.NotConnected
        ServerFailure.SessionRejected -> SeerrError.Unauthorized
        ServerFailure.NotPermitted -> SeerrError.Forbidden
        ServerFailure.NotFound -> SeerrError.NotFound
        is ServerFailure.Refused -> SeerrError.Rejected
        ServerFailure.ServerError -> SeerrError.Server
        is ServerFailure.Unreachable -> SeerrError.Unreachable
        ServerFailure.LocalNetworkDenied -> SeerrError.LocalNetworkDenied
        else -> SeerrError.Unknown
    }

/** Whether the server answered the handshake by asking for a client certificate, which the app does not send. Read by the same rules as [toServerFailure]. */
fun Throwable.wantsClientCertificate(): Boolean = SeerrTransport.read(this) == ServerFailure.ClientCertificateNeeded

/** Whether the server's own certificate was refused: the device does not trust it. No retry of the same address can mend it. */
fun Throwable.certificateRefused(): Boolean = SeerrTransport.read(this) == ServerFailure.CertificateRefused

/** Whether a failure of `GET /auth/me` means the server holds no user for these credentials; the same rule as [SeerrError.rejectsSession]. */
val ServerFailure.rejectsSession: Boolean
    get() = this == ServerFailure.SessionRejected || this == ServerFailure.NotPermitted

/** Only the status and the headers the session interceptor wrote are Seerr's own; the library asks about nothing else of the call. */
private val FACTS = RequestFacts(method = "", path = "")

private class SeerrAnswer(
    private val http: HttpException,
) : FailureClassifier {
    override suspend fun classify(
        status: Int,
        body: ResponseBodyPeek,
        request: RequestFacts,
    ): ServerFailure? =
        when {
            http.rejectsSessionByAnswer() -> ServerFailure.SessionRejected
            http.refusedWithLiveSession() -> ServerFailure.NotPermitted
            status == HTTP_FORBIDDEN ->
                if (body.text().namesQuota()) {
                    ServerFailure.Refused(http.serverMessage())
                } else {
                    ServerFailure.NotPermitted
                }
            status == HTTP_NOT_FOUND || status >= HTTP_SERVER_ERROR_MIN -> null
            else -> ServerFailure.Refused(http.serverMessage())
        }
}

private const val HTTP_SERVER_ERROR_MIN = 500

private object SeerrTransport : TransportFailureReader {
    override fun read(failure: Throwable): ServerFailure? =
        when (failure) {
            is NotConnectedException -> ServerFailure.NotConnected
            is SerializationException -> ServerFailure.Unreadable
            // A failure of the app's own storage: no retry of the server's can mend it.
            is CorruptionException -> ServerFailure.Unknown
            else -> JvmTransportFailures.read(failure)
        }
}
