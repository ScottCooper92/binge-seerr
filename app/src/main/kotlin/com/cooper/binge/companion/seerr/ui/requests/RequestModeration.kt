package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.NoRequestStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.seerr.SeerrEditRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.updateRequest
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.CrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.NoOpAnalytics
import com.cooper.binge.companion.seerr.telemetry.NoOpCrashBreadcrumbs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a screen may ask of a [RequestModeration]: its actions, and none of its flows. A ViewModel that holds one keeps it
 * private and exposes this, with the events as its own (#1048), so the one-state, one-events shape is the one a reader sees.
 */
interface RequestModerationControls {
    fun approve(requestId: Int)

    fun retry(requestId: Int)

    fun setMediaStatus(
        requestId: Int,
        mediaId: Int,
        status: MediaStatusChoice,
        is4k: Boolean,
        seasonNumbers: List<Int> = emptyList(),
    )

    fun clearMedia(
        requestId: Int,
        mediaId: Int,
    )

    /**
     * Deletes one instance's files through Radarr or Sonarr, then removes [removeRequestIds]: the server keeps a title's
     * requests when its files go, so removing them is what lets it be requested again.
     */
    fun deleteMediaFiles(
        requestId: Int,
        mediaId: Int,
        is4k: Boolean,
        removeRequestIds: List<Int> = emptyList(),
    )

    fun decline(
        item: RequestItem,
        blockTitle: Boolean,
    )

    fun remove(
        item: RequestItem,
        blockTitle: Boolean,
    )

    fun blockTitle(item: RequestItem)
}

/**
 * The write side of a request: approve, decline, retry, remove, each optionally blocking the
 * title after a decline or removal, and [blockTitle] to block the title on its own. The server
 * keeps a request when a title is blocked, so a block on its own leaves the request as it was. Owns the per-request acting set and the feedback
 * events; [onModerated] fires after a success so the owner can refresh what it shows.
 */
class RequestModeration(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val connection: SeerrConnection,
    analytics: Analytics = NoOpAnalytics,
    crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    /** The cached list rows, moved with a success so the list agrees before its refresh lands. */
    private val cache: RequestStore = NoRequestStore,
    onModerated: () -> Unit,
) : RequestModerationControls {
    private val runner = ModerationRunner(scope, dispatcher, connection, analytics, crashBreadcrumbs, cache, onModerated)
    private val media = MediaModeration(runner, connection, cache, analytics)

    val actingIds: StateFlow<Set<Int>> = runner.actingIds
    val events: SharedFlow<ModerationEvent> = runner.events

    override fun approve(requestId: Int) =
        runner.moderate(requestId, ModerationEvent.Approved) {
            connection.api().approveRequest(it)
            cache.updateStatus(it, SeerrRequestStatusCode.Approved.raw)
        }

    override fun retry(requestId: Int) = runner.moderate(requestId, ModerationEvent.Retried) { connection.api().retryRequest(it) }

    /**
     * An edit answers its caller directly through [onSettled], as well as on [events], because the events are shared: the
     * editor cannot tell its own answer there from another action's on the same request (#1541). False when another
     * action on the request is still in flight, so the edit was never sent and [onSettled] will not be called.
     */
    fun edit(
        requestId: Int,
        body: SeerrEditRequestBody,
        onSettled: (succeeded: Boolean) -> Unit,
    ): Boolean =
        runner.actThenMaybeBlock(
            item = null,
            requestId = requestId,
            blockTitle = false,
            done = ModerationEvent.Edited,
            doneAndBlocked = ModerationEvent.Edited,
            blockFailed = ModerationEvent.Edited,
            onSettled = onSettled,
        ) { connection.api().updateRequest(it, body) }

    override fun setMediaStatus(
        requestId: Int,
        mediaId: Int,
        status: MediaStatusChoice,
        is4k: Boolean,
        seasonNumbers: List<Int>,
    ) = media.setMediaStatus(requestId, mediaId, status, is4k, seasonNumbers)

    override fun clearMedia(
        requestId: Int,
        mediaId: Int,
    ) {
        media.clearMedia(requestId, mediaId, onSettled = {})
    }

    /** [onSettled] hears how the clear went. False when another action on the request is in flight: nothing was started. */
    fun clearMedia(
        requestId: Int,
        mediaId: Int,
        onSettled: (succeeded: Boolean) -> Unit,
    ): Boolean = media.clearMedia(requestId, mediaId, onSettled)

    override fun deleteMediaFiles(
        requestId: Int,
        mediaId: Int,
        is4k: Boolean,
        removeRequestIds: List<Int>,
    ) {
        media.deleteMediaFiles(requestId, mediaId, is4k, removeRequestIds, onFiles = {})
    }

    /** See [MediaModeration.deleteMediaFiles]: [onFiles] hears the files go, and Seerr's refusal for want of a default server. */
    fun deleteMediaFiles(
        requestId: Int,
        mediaId: Int,
        is4k: Boolean,
        removeRequestIds: List<Int>,
        onFiles: (FilesDeletion) -> Unit,
    ): Boolean = media.deleteMediaFiles(requestId, mediaId, is4k, removeRequestIds, onFiles)

    override fun decline(
        item: RequestItem,
        blockTitle: Boolean,
    ) {
        runner.actThenMaybeBlock(
            item,
            item.id,
            blockTitle,
            ModerationEvent.Declined,
            ModerationEvent.DeclinedAndBlocked,
            ModerationEvent.DeclinedButBlockFailed,
        ) {
            connection.api().declineRequest(it)
            cache.updateStatus(it, SeerrRequestStatusCode.Declined.raw)
        }
    }

    override fun remove(
        item: RequestItem,
        blockTitle: Boolean,
    ) {
        runner.actThenMaybeBlock(
            item,
            item.id,
            blockTitle,
            ModerationEvent.Removed,
            ModerationEvent.RemovedAndBlocked,
            ModerationEvent.RemovedButBlockFailed,
        ) {
            connection.api().deleteRequest(it)
            cache.delete(it)
        }
    }

    /** Blocks the title alone, leaving the request as it is; the owner refreshes what it shows on success. */
    override fun blockTitle(item: RequestItem) = runner.blockTitle(item)
}
