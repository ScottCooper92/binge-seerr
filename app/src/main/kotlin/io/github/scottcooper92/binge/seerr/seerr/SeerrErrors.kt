package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.NotConnectedException
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.Headers
import retrofit2.HttpException
import java.io.IOException

private const val HTTP_SERVER_ERROR_MIN = 500

/** Seerr's status for a request write that went through and created nothing: there was nothing left to request. */
const val HTTP_ACCEPTED = 202

/** The session is gone: Seerr answers 401 to a cookie or key it no longer knows. */
const val HTTP_UNAUTHORIZED = 401

/** A permission the user lacks, or (see [isSessionRejection]) a session the server has dropped. */
const val HTTP_FORBIDDEN = 403

/** Seerr answers 404 for a route a lineage does not serve as well as for a missing record, so callers read it directly. */
const val HTTP_NOT_FOUND = 404

/**
 * Seerr's answer for a target in the wrong state: a request already moderated, retried or no longer pending (#999), and
 * a title someone has already requested, which a submit reads as `already_requested` before anything maps it.
 */
const val HTTP_CONFLICT = 409

/**
 * Why a call to the server failed, as the app's own screens classify it. The gRPC mapping below
 * and this one read the same facts, so the Service and a screen never disagree about a failure:
 * a 401 is the session unless the session still answers (#997), a 403 is a permission unless the
 * body names a quota, a 404 is the title, transport and 5xx are the server or the network, and
 * anything else is a rejection on the merits.
 */
enum class SeerrError {
    NotConnected,
    Unauthorized,
    Forbidden,
    Quota,
    NotFound,
    Unreachable,
    Server,
    Rejected,
    Unknown,
}

/**
 * Whether a failure of `GET /auth/me` means the server holds no user for these credentials: the session
 * expired, the API key was rotated, or the user was deleted. The one rule for it, read by the Service and the
 * screens alike, so the two cannot disagree about a rejected session (#672).
 *
 * Every lineage (Overseerr, Jellyseerr, Seerr) guards `auth/me` with `isAuthenticated()` and no permission,
 * and that middleware answers **403** exactly when no user is attached: `hasPermission(0)` is always true.
 * The server itself never sends 401 there; a 401 comes from a proxy in front of it, and means the same.
 * For `auth/me` only: anywhere else a 403 is a real permission refusal, so callers apply this to that call.
 */
val SeerrError.rejectsSession: Boolean
    get() = this == SeerrError.Unauthorized || this == SeerrError.Forbidden

fun Throwable.toSeerrError(): SeerrError =
    when (this) {
        is NotConnectedException -> SeerrError.NotConnected
        is HttpException ->
            when {
                rejectsSessionByAnswer() -> SeerrError.Unauthorized
                refusedWithLiveSession() -> SeerrError.Forbidden
                code() == HTTP_FORBIDDEN -> if (mentionsQuota()) SeerrError.Quota else SeerrError.Forbidden
                code() == HTTP_NOT_FOUND -> SeerrError.NotFound
                code() >= HTTP_SERVER_ERROR_MIN -> SeerrError.Server
                else -> SeerrError.Rejected
            }
        is IOException -> SeerrError.Unreachable
        else -> SeerrError.Unknown
    }

/**
 * Seerr's failures as the contract's gRPC status codes — the whole error model on this side,
 * because a response message never carries an error field.
 *
 * A 401 is our own session being rejected, which only this app can repair, so it is
 * `UNAUTHENTICATED` and the host sends the user here. A 401 Seerr sent while the session still
 * answers is a refusal instead, `PERMISSION_DENIED`, unless the Service knows that route means
 * something else (#997). A 403 is `PERMISSION_DENIED` unless the
 * body names a quota, which is `RESOURCE_EXHAUSTED` — Seerr returns 403 for both, and the message
 * text is its only signal. A 409 is a target in the wrong state for the action, such as approving a
 * request someone has already approved: `FAILED_PRECONDITION`, so the host refreshes and re-offers (#999).
 * Transport failures and 5xx are `UNAVAILABLE`; any other 4xx is a rejection on the merits,
 * `INVALID_ARGUMENT`.
 *
 * A body this app cannot parse ([SerializationException]) is `UNAVAILABLE` too: the server speaking a shape
 * this app does not expect, which a retry after a server update may fix. Every description here is a fixed
 * string. An exception's own message never crosses to the host, because a serialization failure's message
 * quotes the body it choked on, and that can be the server's secrets (#680). The exception stays on
 * `withCause`, which gRPC never serialises, for local logging.
 */
fun Throwable.toStatusException(): StatusException =
    when (this) {
        is StatusException -> this
        is NotConnectedException -> StatusException(Status.UNAUTHENTICATED.withDescription(message))
        is NothingLeftToRequestException -> StatusException(Status.FAILED_PRECONDITION.withDescription(NOTHING_LEFT_TO_REQUEST))
        is HttpException -> StatusException(httpStatus().withDescription("Seerr answered HTTP ${code()}"))
        is IOException -> StatusException(Status.UNAVAILABLE.withDescription("Seerr could not be reached").withCause(this))
        is SerializationException -> StatusException(Status.UNAVAILABLE.withDescription(UNREADABLE).withCause(this))
        else -> StatusException(Status.INTERNAL.withDescription(UNHANDLED).withCause(this))
    }

private fun HttpException.httpStatus(): Status =
    when {
        rejectsSessionByAnswer() -> Status.UNAUTHENTICATED
        refusedWithLiveSession() -> Status.PERMISSION_DENIED
        code() == HTTP_FORBIDDEN && mentionsBlocklisted() -> Status.FAILED_PRECONDITION
        code() == HTTP_FORBIDDEN -> if (mentionsQuota()) Status.RESOURCE_EXHAUSTED else Status.PERMISSION_DENIED
        code() == HTTP_NOT_FOUND -> Status.NOT_FOUND
        code() == HTTP_CONFLICT -> Status.FAILED_PRECONDITION
        code() >= HTTP_SERVER_ERROR_MIN -> Status.UNAVAILABLE
        else -> Status.INVALID_ARGUMENT
    }

/**
 * A 401 the session was not found alive behind, or a 403 [SeerrSessionInterceptor] confirmed against `auth/me`:
 * the session, not a permission.
 */
private fun HttpException.rejectsSessionByAnswer(): Boolean = isSessionRejection(code(), response()?.headers() ?: Headers.headersOf())

/** A 401 Seerr sent although `auth/me` still answers: a refusal, which a route may read more precisely (#997, #998). */
internal fun HttpException.refusedWithLiveSession(): Boolean = isLiveSessionRefusal(code(), response()?.headers() ?: Headers.headersOf())

/**
 * The error body, peeked rather than consumed so reading it and classifying the same failure (a report,
 * then the screen's own mapping) agree with each other. A body that fails to read is the same as a missing or empty
 * one. This runs inside [toStatusException] itself, so a raw [IOException] here would escape
 * [statusCatching] uncaught rather than become the [Status] the contract expects.
 */
internal fun HttpException.peekedBody(): String =
    attempt {
        response()
            ?.errorBody()
            ?.source()
            ?.peek()
            ?.readUtf8()
    }.getOrNull().orEmpty()

/**
 * Seerr refuses a request for a blocklisted title with a 403 whose message is "This media is blocklisted."
 * (`BlocklistedMediaError` in Seerr's and Jellyseerr's `server/routes/request.ts`). The contract calls that
 * FAILED_PRECONDITION, not a permission (#682).
 */
private fun HttpException.mentionsBlocklisted(): Boolean = peekedBody().contains("blocklisted", ignoreCase = true)

/** Seerr's only signal for a quota breach is the word in its 403 body. */
private fun HttpException.mentionsQuota(): Boolean = peekedBody().namesQuota()

/** Seerr's one quota signal: the word in a 403 body. */
internal fun String.namesQuota(): Boolean = contains("quota", ignoreCase = true)

/**
 * Runs [block] and re-throws any failure as the [StatusException] the contract expects.
 *
 * The broad catch is the point rather than an oversight: this is the edge of the exported Service,
 * where the contract says a failure is a gRPC status code and never a field on a response. Anything
 * that escapes here uncaught crosses the Binder as an unknown, so everything is mapped.
 */
@Suppress("TooGenericExceptionCaught")
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

/**
 * [runCatching] catches everything, cancellation included, which would swallow the cancellation a
 * cancelled scope or a stopped worker sends. This lets that one back out and treats the rest as a
 * failure to classify with [toSeerrError].
 *
 * The one `runCatching` detekt's ForbiddenMethodCall allows (#1173): this is what every other call site uses instead.
 */
@Suppress("ForbiddenMethodCall")
internal inline fun <T> attempt(block: () -> T): Result<T> =
    runCatching(block).onFailure { failure -> if (failure is CancellationException) throw failure }

private const val UNREADABLE = "Seerr returned something this companion could not read"
private const val UNHANDLED = "The companion failed to handle this request"
private const val NOTHING_LEFT_TO_REQUEST = "Seerr has nothing left to request for those seasons"
