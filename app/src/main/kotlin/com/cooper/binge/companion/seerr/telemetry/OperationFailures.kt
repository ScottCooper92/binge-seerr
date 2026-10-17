package com.cooper.binge.companion.seerr.telemetry

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.NotConnectedException
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toStatusException
import io.grpc.StatusException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

private const val UNAVAILABLE = "none"
private const val UNKNOWN = "unknown"

/**
 * Reports that [operation] failed against the connected server, so a failure only one server version
 * produces can be found from the field (#539). Only an HTTP answer or an unclassified failure is sent.
 * Skipped: cancellation, the network, this app's own [StatusException] or `NotConnectedException`, and a
 * dead session: whatever [toSeerrError] reads as [SeerrError.Unauthorized], a 401 or a 403 that
 * `SeerrSessionInterceptor` confirmed (#1165). Seerr sends an expired sign-in as that 403 (#689); an
 * unmarked 403 is a permission refusal and is still sent.
 *
 * @param operation one of this app's fixed names. Every event param is low-cardinality, never free text:
 *   no id, title, URL, host or response body, the rule `CrashBreadcrumbs` states for its notes.
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
