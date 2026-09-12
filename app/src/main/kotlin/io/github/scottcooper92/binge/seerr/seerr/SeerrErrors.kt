package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.NotConnectedException
import io.grpc.Status
import io.grpc.StatusException
import retrofit2.HttpException
import java.io.IOException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * Seerr's failures as the contract's gRPC status codes — the whole error model on this side,
 * because a response message never carries an error field.
 *
 * A 401 is our own session being rejected, which only this app can repair, so it is
 * `UNAUTHENTICATED` and the host sends the user here. A 403 is `PERMISSION_DENIED` unless the
 * body names a quota, which is `RESOURCE_EXHAUSTED` — Seerr returns 403 for both, and the message
 * text is its only signal. Transport failures and 5xx are `UNAVAILABLE`; any other 4xx is a
 * rejection on the merits, `INVALID_ARGUMENT`.
 */
fun Throwable.toStatusException(): StatusException =
    when (this) {
        is StatusException -> this
        is NotConnectedException -> StatusException(Status.UNAUTHENTICATED.withDescription(message))
        is HttpException -> StatusException(httpStatus().withDescription("Seerr answered HTTP ${code()}"))
        is IOException -> StatusException(Status.UNAVAILABLE.withDescription("Seerr could not be reached").withCause(this))
        else -> StatusException(Status.INTERNAL.withDescription(message).withCause(this))
    }

private fun HttpException.httpStatus(): Status =
    when {
        code() == HTTP_UNAUTHORIZED -> Status.UNAUTHENTICATED
        code() == HTTP_FORBIDDEN -> if (mentionsQuota()) Status.RESOURCE_EXHAUSTED else Status.PERMISSION_DENIED
        code() == HTTP_NOT_FOUND -> Status.NOT_FOUND
        code() >= HTTP_SERVER_ERROR_MIN -> Status.UNAVAILABLE
        else -> Status.INVALID_ARGUMENT
    }

/** Seerr's only signal for a quota breach is the word in its 403 body. */
private fun HttpException.mentionsQuota(): Boolean =
    response()
        ?.errorBody()
        ?.string()
        .orEmpty()
        .contains("quota", ignoreCase = true)

/** Runs [block] and re-throws any failure as the [StatusException] the contract expects. */
suspend inline fun <T> statusCatching(block: () -> T): T =
    try {
        block()
    } catch (e: StatusException) {
        throw e
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toStatusException()
    }
