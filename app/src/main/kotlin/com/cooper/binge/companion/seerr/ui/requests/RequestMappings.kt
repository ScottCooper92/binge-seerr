package com.cooper.binge.companion.seerr.ui.requests

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled._4k
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.component.MediaTypeTagType
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.state.RequestStateTone
import com.cooper.binge.companion.seerr.ui.state.labelRes
import com.cooper.binge.companion.seerr.ui.state.tone

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

/** The sort's glyph in the choice sheet: the date a request came in, or the last time it changed. */
internal fun RequestSort.icon(): ImageVector =
    when (this) {
        RequestSort.Added -> Icons.Filled.Event
        RequestSort.Modified -> Icons.Filled.Update
    }

/** Doubles as the row's title when the lookup failed. */
@StringRes
internal fun RequestMediaType.labelRes(): Int =
    when (this) {
        RequestMediaType.Movie -> R.string.media_type_movie
        RequestMediaType.Tv -> R.string.media_type_tv
    }

/** Both are Movie/Tv enums, but distinct types — this app's request model and the design system's tag. */
internal fun RequestMediaType.toTagType(): MediaTypeTagType =
    when (this) {
        RequestMediaType.Movie -> MediaTypeTagType.Movie
        RequestMediaType.Tv -> MediaTypeTagType.Tv
    }

data class RequestRowChip(
    @param:StringRes val labelRes: Int,
    val tone: RequestStateTone,
    val icon: ImageVector,
)

/**
 * The one glyph for each state a request or its title can be in. The status chip, the TV poster chip and the Mark as
 * sheet all read these, so a state is never drawn two ways.
 */
internal object StatusGlyphs {
    val Declined = Icons.Filled.Close
    val Failed = Icons.Filled.ErrorOutline
    val Available = Icons.Filled.CheckCircle
    val PartiallyAvailable = Icons.Filled.Done
    val Processing = Icons.Filled.Download
    val Queued = Icons.Filled.Schedule
    val Approved = Icons.Filled.Check
    val Pending = Icons.Filled.HourglassEmpty
    val Unknown = Icons.AutoMirrored.Filled.HelpOutline
    val Blocked = Icons.Filled.Block
}

/**
 * The row's one status pill. `mediaStatus` is the source of truth for the title's own outcome —
 * available, blocklisted, deleted, processing — and wins over the request's own decision in every
 * case but a terminal one; `download` only refines a `Processing` chip with what Radarr/Sonarr's
 * queue adds (an active fraction/ETA), never stands in for reading `mediaStatus` at all. Only once
 * neither the request nor the media has anything more specific to say does the bare `Approved`
 * request status get to speak for itself, and only a still-unrecognised `mediaStatus` lets it.
 */
fun RequestItem.statusChip(): RequestRowChip =
    when {
        status == SeerrRequestStatusCode.Declined ->
            RequestRowChip(
                R.string.request_state_declined,
                RequestStateTone.Declined,
                StatusGlyphs.Declined,
            )
        status == SeerrRequestStatusCode.Failed ->
            RequestRowChip(
                R.string.request_state_failed,
                RequestStateTone.Declined,
                StatusGlyphs.Failed,
            )
        mediaStatus == SeerrMediaStatusCode.Available ->
            RequestRowChip(
                R.string.media_state_available,
                RequestStateTone.Success,
                StatusGlyphs.Available,
            )
        mediaStatus == SeerrMediaStatusCode.PartiallyAvailable ->
            RequestRowChip(R.string.media_state_partially_available, RequestStateTone.Success, StatusGlyphs.PartiallyAvailable)
        mediaStatus == SeerrMediaStatusCode.Blocklisted ->
            RequestRowChip(SeerrMediaStatusCode.Blocklisted.labelRes(), SeerrMediaStatusCode.Blocklisted.tone(), StatusGlyphs.Blocked)
        mediaStatus == SeerrMediaStatusCode.Deleted ->
            RequestRowChip(SeerrMediaStatusCode.Deleted.labelRes(), SeerrMediaStatusCode.Deleted.tone(), StatusGlyphs.Blocked)
        mediaStatus == SeerrMediaStatusCode.Processing && download?.downloading == true ->
            RequestRowChip(R.string.media_state_processing, RequestStateTone.Active, StatusGlyphs.Processing)
        mediaStatus == SeerrMediaStatusCode.Processing && download != null ->
            RequestRowChip(R.string.request_state_queued, RequestStateTone.Pending, StatusGlyphs.Queued)
        mediaStatus == SeerrMediaStatusCode.Processing ->
            RequestRowChip(SeerrMediaStatusCode.Processing.labelRes(), SeerrMediaStatusCode.Processing.tone(), StatusGlyphs.Processing)
        (status == SeerrRequestStatusCode.Approved || status == SeerrRequestStatusCode.Completed) &&
            (mediaStatus == null || mediaStatus == SeerrMediaStatusCode.Unknown) ->
            RequestRowChip(R.string.request_state_approved, RequestStateTone.Success, StatusGlyphs.Approved)
        else -> RequestRowChip(R.string.request_state_pending, RequestStateTone.Pending, StatusGlyphs.Pending)
    }

/**
 * The server's own rules for one request: approve, decline and retry are `MANAGE_REQUESTS`;
 * removing is that too, or the requester's own pending request; blocking rides a decline or
 * removal where the lineage has a blocklist and the user may manage it.
 */
fun RequestItem.actions(scope: ModerationScope): RequestActions {
    val pending = status == null || status == SeerrRequestStatusCode.Pending
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

/** Whether the page has a primary action to pin: something reviewable, a media record to manage, or an edit. */
internal val RequestDetail.hasPrimaryAction: Boolean
    get() = actions.any || media?.canManage == true || canEdit

internal fun MediaStatusChoice.labelRes(): Int =
    when (this) {
        MediaStatusChoice.Available -> R.string.media_state_available
        MediaStatusChoice.PartiallyAvailable -> R.string.media_state_partially_available
        MediaStatusChoice.Processing -> R.string.media_state_processing
        MediaStatusChoice.Pending -> R.string.request_state_pending
        MediaStatusChoice.Unknown -> R.string.media_state_unknown
    }

/** The availability's glyph in the Mark as sheet: the one the status chips draw for that state. */
internal fun MediaStatusChoice.icon(): ImageVector =
    when (this) {
        MediaStatusChoice.Available -> StatusGlyphs.Available
        MediaStatusChoice.PartiallyAvailable -> StatusGlyphs.PartiallyAvailable
        MediaStatusChoice.Processing -> StatusGlyphs.Processing
        MediaStatusChoice.Pending -> StatusGlyphs.Pending
        MediaStatusChoice.Unknown -> StatusGlyphs.Unknown
    }

/** An instance's version label: Standard or 4K. */
@StringRes
internal fun MediaInstance.labelRes(): Int = if (is4k) R.string.settings_service_4k else R.string.media_instance_standard

/** An instance's glyph in Manage media: the 4K badge for the 4K one, else the title's own kind. */
internal fun MediaInstance.icon(isTv: Boolean): ImageVector =
    when {
        is4k -> Icons.Filled._4k
        isTv -> Icons.Filled.Tv
        else -> Icons.Filled.Movie
    }
