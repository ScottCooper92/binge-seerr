package com.cooper.binge.companion.seerr.ui.requests

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusBody
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusSeasonBody
import com.cooper.binge.companion.seerr.seerr.isNoDefaultServerRefusal
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.operationFailed

/**
 * The moderation actions that act on a title's media record rather than on one request: marking it, clearing its data and
 * deleting its files. They run through the same [ModerationRunner] as [RequestModeration], so a request is never acted on
 * twice at once and the events arrive on one stream.
 */
internal class MediaModeration(
    private val runner: ModerationRunner,
    private val connection: SeerrConnection,
    private val cache: RequestStore,
    private val analytics: Analytics,
) {
    /**
     * [seasonNumbers] is this request's own seasons - what marking *this* title available should
     * mean here, rather than every season the show has. The server only reads it for
     * [MediaStatusChoice.Available] on a TV title, so it is sent only then.
     */
    fun setMediaStatus(
        requestId: Int,
        mediaId: Int,
        status: MediaStatusChoice,
        is4k: Boolean,
        seasonNumbers: List<Int>,
        onSettled: (succeeded: Boolean) -> Unit,
    ): Boolean =
        runner.actThenMaybeBlock(
            item = null,
            requestId = requestId,
            blockTitle = false,
            done = ModerationEvent.MediaStatusSet,
            doneAndBlocked = ModerationEvent.MediaStatusSet,
            blockFailed = ModerationEvent.MediaStatusSet,
            onSettled = onSettled,
        ) {
            val seasons =
                seasonNumbers.map { SeerrMediaStatusSeasonBody(it) }.takeIf { status == MediaStatusChoice.Available && it.isNotEmpty() }
            connection.api().setMediaStatus(mediaId, status.path, SeerrMediaStatusBody(is4k, seasons))
        }

    /** [onSettled] hears how the clear went. False when another action on the request is in flight: nothing was started. */
    fun clearMedia(
        requestId: Int,
        mediaId: Int,
        onSettled: (succeeded: Boolean) -> Unit,
    ): Boolean =
        runner.actThenMaybeBlock(
            item = null,
            requestId = requestId,
            blockTitle = false,
            done = ModerationEvent.MediaCleared,
            doneAndBlocked = ModerationEvent.MediaCleared,
            blockFailed = ModerationEvent.MediaCleared,
            onSettled = onSettled,
            syncFirst = true,
        ) {
            connection.api().deleteMedia(mediaId)
            cache.deleteTitleOf(it)
        }

    /**
     * [onFiles] hears that the files went before any request is removed, so a page can say so even when a removal then
     * fails, and hears Seerr's refusal for want of a default server, which the page shows in place (#1726). The removals
     * run inside the action, so the reload that follows sees them.
     */
    fun deleteMediaFiles(
        requestId: Int,
        mediaId: Int,
        is4k: Boolean,
        removeRequestIds: List<Int>,
        onFiles: (FilesDeletion) -> Unit,
    ): Boolean {
        var allRemoved = true
        var removedThisRequest = false
        return runner.act(
            requestId = requestId,
            syncFirst = true,
            refusal = { failure ->
                failure.takeIf { it.isNoDefaultServerRefusal() }?.let {
                    onFiles(FilesDeletion.NoDefaultServer)
                    ModerationEvent.MediaFilesNoDefaultServer
                } ?: run {
                    onFiles(FilesDeletion.Failed)
                    null
                }
            },
            done =
                if (removeRequestIds.isEmpty()) {
                    ModerationEvent.MediaFilesDeleted
                } else {
                    // Only a label for the breadcrumb and failure log; the outcome below is the event sent.
                    ModerationEvent.MediaFilesAndRequestsDeleted(false)
                },
            outcome = {
                when {
                    removeRequestIds.isEmpty() -> ModerationEvent.MediaFilesDeleted
                    allRemoved -> ModerationEvent.MediaFilesAndRequestsDeleted(removedThisRequest)
                    else -> ModerationEvent.MediaFilesDeletedRequestsKept(removedThisRequest)
                }
            },
        ) {
            val api = connection.api()
            api.deleteMediaFiles(mediaId, is4k)
            onFiles(FilesDeletion.Deleted)
            removeRequestIds.forEach { id ->
                attempt { api.deleteRequest(id) }
                    .onSuccess { cache.delete(id) }
                    .onSuccess { removedThisRequest = removedThisRequest || id == requestId }
                    .onFailure {
                        allRemoved = false
                        analytics.operationFailed("moderate_remove_with_files", it, connection)
                    }
            }
        }
    }
}
