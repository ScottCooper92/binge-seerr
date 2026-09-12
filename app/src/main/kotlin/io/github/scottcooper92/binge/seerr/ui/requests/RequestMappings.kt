package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.annotation.StringRes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone

@StringRes
internal fun RequestFilter.labelRes(): Int =
    when (this) {
        RequestFilter.All -> R.string.requests_filter_all
        RequestFilter.Pending -> R.string.requests_filter_pending
        RequestFilter.Approved -> R.string.requests_filter_approved
        RequestFilter.Processing -> R.string.requests_filter_processing
        RequestFilter.Available -> R.string.requests_filter_available
        RequestFilter.Unavailable -> R.string.requests_filter_unavailable
        RequestFilter.Failed -> R.string.requests_filter_failed
    }

@StringRes
internal fun RequestFilter.emptyMessageRes(): Int =
    when (this) {
        RequestFilter.All -> R.string.requests_empty_all
        RequestFilter.Pending -> R.string.requests_empty_pending
        RequestFilter.Approved -> R.string.requests_empty_approved
        RequestFilter.Processing -> R.string.requests_empty_processing
        RequestFilter.Available -> R.string.requests_empty_available
        RequestFilter.Unavailable -> R.string.requests_empty_unavailable
        RequestFilter.Failed -> R.string.requests_empty_failed
    }

@StringRes
internal fun RequestSort.labelRes(): Int =
    when (this) {
        RequestSort.Added -> R.string.requests_sort_added
        RequestSort.Modified -> R.string.requests_sort_modified
    }

/** Doubles as the row's title when the lookup failed. */
@StringRes
internal fun RequestMediaType.labelRes(): Int =
    when (this) {
        RequestMediaType.Movie -> R.string.media_type_movie
        RequestMediaType.Tv -> R.string.media_type_tv
    }

data class RequestRowChip(
    @param:StringRes val labelRes: Int,
    val tone: RequestStateTone,
)

/**
 * The row's one status pill, folding the request's decision and the title's availability: a
 * decision that ended it, then an outcome, then the bare approval state.
 */
fun RequestItem.statusChip(): RequestRowChip =
    when {
        status == SeerrRequestStatusCode.Declined -> RequestRowChip(R.string.request_state_declined, RequestStateTone.Declined)
        status == SeerrRequestStatusCode.Failed -> RequestRowChip(R.string.request_state_failed, RequestStateTone.Declined)
        mediaStatus == SeerrMediaStatusCode.Available -> RequestRowChip(R.string.media_state_available, RequestStateTone.Success)
        mediaStatus == SeerrMediaStatusCode.PartiallyAvailable ->
            RequestRowChip(R.string.media_state_partially_available, RequestStateTone.Success)
        download?.downloading == true -> RequestRowChip(R.string.media_state_processing, RequestStateTone.Active)
        download != null -> RequestRowChip(R.string.request_state_queued, RequestStateTone.Pending)
        status == SeerrRequestStatusCode.Approved || status == SeerrRequestStatusCode.Completed ->
            RequestRowChip(R.string.request_state_approved, RequestStateTone.Success)
        else -> RequestRowChip(R.string.request_state_pending, RequestStateTone.Pending)
    }

/**
 * The server's own rules for one request: approve, decline and retry are `MANAGE_REQUESTS`;
 * removing is that too, or the requester's own pending request; blocking rides a decline or
 * removal where the lineage has a blocklist and the user may manage it.
 */
fun RequestItem.actions(scope: ModerationScope): RequestActions {
    val pending = status == null || status == SeerrRequestStatusCode(PENDING_STATUS)
    val moderator = scope.permissions.canManageRequests
    val own = requestedById != null && requestedById == scope.currentUserId
    return RequestActions(
        canApprove = moderator && pending,
        canDecline = moderator && pending,
        canRetry = moderator && status == SeerrRequestStatusCode.Failed,
        canRemove = moderator || (own && pending),
        canBlock = scope.hasBlocklist && scope.permissions.canManageBlocklist && mediaStatus != SeerrMediaStatusCode.Blocklisted,
    )
}

private const val PENDING_STATUS = 1
