package com.cooper.binge.companion.seerr.ui.requests

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.NoRequestStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.seerr.SeerrAddToBlocklistBody
import com.cooper.binge.companion.seerr.seerr.SeerrEditRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusBody
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusSeasonBody
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.addToBlocklistOnce
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.updateRequest
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.CrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.NoOpAnalytics
import com.cooper.binge.companion.seerr.telemetry.NoOpCrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.operationFailed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
                Approved, Retried, Edited, Declined, DeclinedAndBlocked, DeclinedButBlockFailed,
                Blocked, BlockFailed, MediaStatusSet, MediaFilesDeleted, is Failed,
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

    data class Failed(
        val error: SeerrError,
    ) : ModerationEvent
}

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

    fun deleteMediaFiles(
        requestId: Int,
        mediaId: Int,
        is4k: Boolean,
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

private const val HTTP_NOT_FOUND = 404

/**
 * The write side of a request: approve, decline, retry, remove, each optionally blocking the
 * title after a decline or removal, and [blockTitle] to block the title on its own. The server
 * keeps a request when a title is blocked, so a block on its own leaves the request as it was. Owns the per-request acting set and the feedback
 * events; [onModerated] fires after a success so the owner can refresh what it shows.
 */
class RequestModeration(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val connection: SeerrConnection,
    private val analytics: Analytics = NoOpAnalytics,
    private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    /** The cached list rows, moved with a success so the list agrees before its refresh lands. */
    private val cache: RequestStore = NoRequestStore,
    private val onModerated: () -> Unit,
) : RequestModerationControls {
    private val acting = MutableStateFlow<Set<Int>>(emptySet())
    val actingIds: StateFlow<Set<Int>> = acting.asStateFlow()

    private val eventFlow = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ModerationEvent> = eventFlow.asSharedFlow()

    override fun approve(requestId: Int) =
        moderate(requestId, ModerationEvent.Approved) {
            connection.api().approveRequest(it)
            cache.updateStatus(it, SeerrRequestStatusCode.Approved.raw)
        }

    override fun retry(requestId: Int) = moderate(requestId, ModerationEvent.Retried) { connection.api().retryRequest(it) }

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
        actThenMaybeBlock(
            item = null,
            requestId = requestId,
            blockTitle = false,
            done = ModerationEvent.Edited,
            doneAndBlocked = ModerationEvent.Edited,
            blockFailed = ModerationEvent.Edited,
            onSettled = onSettled,
        ) { connection.api().updateRequest(it, body) }

    /**
     * [seasonNumbers] is this request's own seasons - what marking *this* title available should
     * mean here, rather than every season the show has. The server only reads it for
     * [MediaStatusChoice.Available] on a TV title, so it is sent only then.
     */
    override fun setMediaStatus(
        requestId: Int,
        mediaId: Int,
        status: MediaStatusChoice,
        is4k: Boolean,
        seasonNumbers: List<Int>,
    ) = moderate(requestId, ModerationEvent.MediaStatusSet) {
        val seasons =
            seasonNumbers.map { SeerrMediaStatusSeasonBody(it) }.takeIf { status == MediaStatusChoice.Available && it.isNotEmpty() }
        connection.api().setMediaStatus(mediaId, status.path, SeerrMediaStatusBody(is4k, seasons))
    }

    /** The server removes every request for the title along with its record, this one included. */
    override fun clearMedia(
        requestId: Int,
        mediaId: Int,
    ) = moderate(requestId, ModerationEvent.MediaCleared) { connection.api().deleteMedia(mediaId) }

    override fun deleteMediaFiles(
        requestId: Int,
        mediaId: Int,
        is4k: Boolean,
    ) = moderate(requestId, ModerationEvent.MediaFilesDeleted) { connection.api().deleteMediaFiles(mediaId, is4k) }

    override fun decline(
        item: RequestItem,
        blockTitle: Boolean,
    ) = actThenMaybeBlock(
        item,
        blockTitle,
        ModerationEvent.Declined,
        ModerationEvent.DeclinedAndBlocked,
        ModerationEvent.DeclinedButBlockFailed,
    ) {
        connection.api().declineRequest(it)
        cache.updateStatus(it, SeerrRequestStatusCode.Declined.raw)
    }

    override fun remove(
        item: RequestItem,
        blockTitle: Boolean,
    ) = actThenMaybeBlock(
        item,
        blockTitle,
        ModerationEvent.Removed,
        ModerationEvent.RemovedAndBlocked,
        ModerationEvent.RemovedButBlockFailed,
    ) {
        connection.api().deleteRequest(it)
        cache.delete(it)
    }

    private fun moderate(
        requestId: Int,
        success: ModerationEvent,
        action: suspend (Int) -> Unit,
    ) {
        actThenMaybeBlock(null, requestId, false, success, success, success, action = action)
    }

    private fun actThenMaybeBlock(
        item: RequestItem,
        blockTitle: Boolean,
        done: ModerationEvent,
        doneAndBlocked: ModerationEvent,
        blockFailed: ModerationEvent,
        action: suspend (Int) -> Unit,
    ) {
        actThenMaybeBlock(item, item.id, blockTitle, done, doneAndBlocked, blockFailed, action = action)
    }

    /**
     * Acting is raised before the launch so a second tap is swallowed at once, and cleared as soon
     * as the action settles, before the refresh and the longer block follow-up. False when an action on the request is
     * already in flight, so this one was not started; [onSettled] hears how a started one went, before the event does.
     *
     * Launched on [dispatcher] rather than inheriting `scope`'s own, so [onModerated]'s reload and
     * this launch's own state updates never touch `Dispatchers.Main` - including the tail end of a
     * call still in flight after the owning screen is gone (#177).
     */
    private fun actThenMaybeBlock(
        item: RequestItem?,
        requestId: Int,
        blockTitle: Boolean,
        done: ModerationEvent,
        doneAndBlocked: ModerationEvent,
        blockFailed: ModerationEvent,
        onSettled: (succeeded: Boolean) -> Unit = {},
        action: suspend (Int) -> Unit,
    ): Boolean {
        if (requestId in acting.value) return false
        acting.update { it + requestId }
        crashBreadcrumbs.key("request_id", requestId.toString())
        crashBreadcrumbs.log("moderating request: ${done.actionLabel()}")
        scope.launch(dispatcher) {
            val result = attempt { action(requestId) }
            acting.update { it - requestId }
            onSettled(result.isSuccess)
            result
                .onSuccess {
                    onModerated()
                    val event =
                        if (blockTitle && item != null) {
                            if (block(item)) doneAndBlocked else blockFailed
                        } else {
                            done
                        }
                    analytics.event(AnalyticsEvents.REQUEST_MODERATED, mapOf(AnalyticsEvents.PARAM_ACTION to event.actionLabel()))
                    eventFlow.emit(event)
                    syncCache(requestId)
                }.onFailure {
                    analytics.operationFailed("moderate_${done.actionLabel()}", it, connection)
                    eventFlow.emit(ModerationEvent.Failed(it.toSeerrError()))
                }
        }
        return true
    }

    /**
     * Writes what the server now says into the cache every list reads, so each one updates without being told (#1665):
     * the request's own status, or its removal when the server no longer has it, and the chip counts. Best effort, and after the
     * result is reported so a slow read holds nothing up: one that fails leaves the optimistic write the action made.
     */
    private suspend fun syncCache(requestId: Int) {
        attempt { connection.api().request(requestId) }.fold(
            onSuccess = { cache.updateServerState(requestId, it.status?.raw, it.media.status?.raw) },
            onFailure = { if ((it as? HttpException)?.code() == HTTP_NOT_FOUND) cache.delete(requestId) },
        )
        attempt { connection.api().requestCount() }.onSuccess {
            cache.setCounts(RequestCounts(it.total, it.pending, it.approved, it.processing, it.available))
        }
    }

    /** Blocks the title alone, leaving the request as it is; the owner refreshes what it shows on success. */
    override fun blockTitle(item: RequestItem) {
        if (item.id in acting.value) return
        acting.update { it + item.id }
        scope.launch(dispatcher) {
            val blocked = block(item)
            acting.update { it - item.id }
            if (blocked) onModerated()
            val event = if (blocked) ModerationEvent.Blocked else ModerationEvent.BlockFailed
            analytics.event(AnalyticsEvents.REQUEST_MODERATED, mapOf(AnalyticsEvents.PARAM_ACTION to event.actionLabel()))
            eventFlow.emit(event)
            if (blocked) syncCache(item.id)
        }
    }

    private suspend fun block(item: RequestItem): Boolean =
        attempt {
            val mediaType = item.mediaType.seerrMediaType()
            val user = connection.authenticatedUser().id
            connection.api().addToBlocklistOnce(
                connection.profile().blocklistPath,
                SeerrAddToBlocklistBody(item.tmdbId, mediaType, item.title.orEmpty(), user),
            )
        }.onFailure { analytics.operationFailed("block_title", it, connection) }
            .isSuccess
}

/** The [AnalyticsEvents.PARAM_ACTION] value for [AnalyticsEvents.REQUEST_MODERATED]; [ModerationEvent.Failed] never reaches this. */
private fun ModerationEvent.actionLabel(): String =
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
        is ModerationEvent.Failed -> "failed"
    }
