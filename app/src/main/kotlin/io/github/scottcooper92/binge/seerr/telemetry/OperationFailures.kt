package io.github.scottcooper92.binge.seerr.telemetry

import io.github.scottcooper92.binge.seerr.auth.NotConnectedException
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.seerr.toStatusException
import io.grpc.StatusException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

private const val UNAVAILABLE = "none"
private const val UNKNOWN = "unknown"

/**
 * Reports that [operation] failed against the connected server, so a failure only one server
 * version produces can be found from the field (#539): "400 on `unblock_title`, Jellyseerr 2.x only".
 *
 * Only a failure that can say something about version compatibility is sent. Skipped: cancellation,
 * a dead session (only this app can repair it), a transport failure (the network),
 * and a [io.grpc.StatusException] or `NotConnectedException` (this app refusing, or not connected,
 * before the server was asked). What remains is an HTTP answer the server chose to give, and an
 * unclassified failure, which is usually a body this app could not read.
 *
 * A dead session is whatever [toSeerrError] reads as [SeerrError.Unauthorized]: a 401, or a 403
 * `SeerrSessionInterceptor` marked after `auth/me` refused the same credentials. On Seerr an expired
 * sign-in arrives as that marked 403, so skipping only a 401 would report it (#689). An unmarked 403
 * is a permission refusal and is still sent.
 *
 * Every param is low-cardinality and none is free text: never an id, a title, a URL, a host or a
 * response body, the rule `CrashBreadcrumbs` states for its notes. [operation] must be one of this
 * app's fixed names.
 */
suspend fun Analytics.operationFailed(
    operation: String,
    failure: Throwable,
    connection: SeerrConnection,
) {
    if (!failure.isVersionSignal()) return
    operationFailed(operation, failure, attempt { connection.profile() }.getOrNull())
}

/** As above, with the [profile] already in hand; null when it could not be read. */
fun Analytics.operationFailed(
    operation: String,
    failure: Throwable,
    profile: SeerrServerProfile?,
) {
    if (!failure.isVersionSignal()) return
    val http = (failure as? HttpException)?.code()?.toString() ?: UNAVAILABLE
    event(
        AnalyticsEvents.REQUEST_OPERATION_FAILED,
        mapOf(
            AnalyticsEvents.PARAM_OPERATION to operation,
            AnalyticsEvents.PARAM_HTTP_STATUS to http,
            AnalyticsEvents.PARAM_GRPC_STATUS to
                failure
                    .toStatusException()
                    .status.code.name,
            AnalyticsEvents.PARAM_SERVER_LINEAGE to (profile?.variant?.name?.lowercase() ?: UNKNOWN),
            AnalyticsEvents.PARAM_SERVER_VERSION to (profile?.version?.label ?: UNKNOWN),
        ),
    )
}

private fun Throwable.isVersionSignal(): Boolean =
    when (this) {
        is CancellationException -> false
        is HttpException -> toSeerrError() != SeerrError.Unauthorized
        is IOException, is StatusException, is NotConnectedException -> false
        else -> true
    }
