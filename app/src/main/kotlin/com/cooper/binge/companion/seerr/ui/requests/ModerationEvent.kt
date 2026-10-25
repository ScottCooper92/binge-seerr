package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents

/** One-shot feedback for a moderation; the partial cases say the action landed even though the block did not. */
sealed interface ModerationEvent {
    /**
     * The request is gone from the server afterwards, so a page showing it has nothing left to show.
     * Clearing the media record takes the request with it, which is why it counts.
     *
     * Exhaustive rather than a set, so a new event is a compile error until it says which it is.
     */
    val removesTheRequest: Boolean
        get() =
            when (this) {
                Removed, RemovedAndBlocked, RemovedButBlockFailed, MediaCleared -> true
                is MediaFilesAndRequestsDeleted -> removedThisRequest
                Approved, Retried, Edited, Declined, DeclinedAndBlocked, DeclinedButBlockFailed,
                Blocked, BlockFailed, MediaStatusSet, MediaFilesDeleted, MediaFilesDeletedRequestsKept, is Failed,
                -> false
            }

    data object Approved : ModerationEvent

    data object Retried : ModerationEvent

    data object Edited : ModerationEvent

    data object Declined : ModerationEvent

    data object DeclinedAndBlocked : ModerationEvent

    data object DeclinedButBlockFailed : ModerationEvent

    data object Removed : ModerationEvent

    data object RemovedAndBlocked : ModerationEvent

    data object RemovedButBlockFailed : ModerationEvent

    /** The title alone was blocked; the request is left as it was. */
    data object Blocked : ModerationEvent

    data object BlockFailed : ModerationEvent

    data object MediaStatusSet : ModerationEvent

    data object MediaCleared : ModerationEvent

    data object MediaFilesDeleted : ModerationEvent

    /** The files went, and so did every request asked to go with them; [removedThisRequest] when one was the page's own. */
    data class MediaFilesAndRequestsDeleted(
        val removedThisRequest: Boolean,
    ) : ModerationEvent

    /** The files went, but at least one of the requests asked to go with them did not. */
    data object MediaFilesDeletedRequestsKept : ModerationEvent

    data class Failed(
        val error: SeerrError,
    ) : ModerationEvent
}

/** The [AnalyticsEvents.PARAM_ACTION] value for [AnalyticsEvents.REQUEST_MODERATED]; [ModerationEvent.Failed] never reaches this. */
internal fun ModerationEvent.actionLabel(): String =
    when (this) {
        ModerationEvent.Approved -> "approved"
        ModerationEvent.Retried -> "retried"
        ModerationEvent.Edited -> "edited"
        ModerationEvent.Declined -> "declined"
        ModerationEvent.DeclinedAndBlocked -> "declined_and_blocked"
        ModerationEvent.DeclinedButBlockFailed -> "declined_block_failed"
        ModerationEvent.Removed -> "removed"
        ModerationEvent.RemovedAndBlocked -> "removed_and_blocked"
        ModerationEvent.RemovedButBlockFailed -> "removed_block_failed"
        ModerationEvent.Blocked -> "blocked"
        ModerationEvent.BlockFailed -> "block_failed"
        ModerationEvent.MediaStatusSet -> "media_status_set"
        ModerationEvent.MediaCleared -> "media_cleared"
        ModerationEvent.MediaFilesDeleted -> "media_files_deleted"
        is ModerationEvent.MediaFilesAndRequestsDeleted -> "media_files_and_requests_deleted"
        ModerationEvent.MediaFilesDeletedRequestsKept -> "media_files_deleted_requests_kept"
        is ModerationEvent.Failed -> "failed"
    }
