package com.cooper.binge.companion.seerr.ui.requests

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.review.NoReviewSignals
import com.cooper.binge.companion.seerr.review.ReviewSignalSink
import com.cooper.binge.companion.seerr.seerr.SeerrAddToBlocklistBody
import com.cooper.binge.companion.seerr.seerr.addToBlocklistOnce
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.CrashBreadcrumbs
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

private const val HTTP_NOT_FOUND = 404

/**
 * What every moderation action runs through: the per-request acting set, the feedback events, and the one place an action
 * is launched, settled and reported. [RequestModeration] and [MediaModeration] share one, so they share one acting set and
 * one events stream. [onModerated] fires after a success so the owner can refresh what it shows.
 */
internal class ModerationRunner(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val connection: SeerrConnection,
    private val analytics: Analytics,
    private val crashBreadcrumbs: CrashBreadcrumbs,
    private val cache: RequestStore,
    private val onModerated: () -> Unit,
    private val reviewSignals: ReviewSignalSink = NoReviewSignals,
) {
    private val acting = MutableStateFlow<Set<Int>>(emptySet())
    val actingIds: StateFlow<Set<Int>> = acting.asStateFlow()

    private val eventFlow = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ModerationEvent> = eventFlow.asSharedFlow()

    /** Says [event] to whoever shows this stream, for an outcome the page itself produced, such as a failed pull. */
    fun announce(event: ModerationEvent) {
        eventFlow.tryEmit(event)
    }

    fun moderate(
        requestId: Int,
        success: ModerationEvent,
        action: suspend (Int) -> Unit,
    ) {
        actThenMaybeBlock(null, requestId, false, success, success, { success }, action = action)
    }

    fun actThenMaybeBlock(
        item: RequestItem?,
        requestId: Int,
        blockTitle: Boolean,
        done: ModerationEvent,
        doneAndBlocked: ModerationEvent,
        blockFailed: (RequestItem) -> ModerationEvent,
        onSettled: (succeeded: Boolean) -> Unit = {},
        syncFirst: Boolean = false,
        action: suspend (Int) -> Unit,
    ): Boolean =
        act(
            requestId = requestId,
            done = done,
            onSettled = onSettled,
            syncFirst = syncFirst,
            outcome = {
                if (blockTitle && item != null) {
                    if (block(item)) doneAndBlocked else blockFailed(item)
                } else {
                    done
                }
            },
            action = action,
        )

    /**
     * Acting is raised before the launch so a second tap is swallowed at once, and cleared as soon as the action settles.
     * False when an action on the request is already in flight; [onSettled] hears how a started one went, before the event.
     * Launched on [dispatcher] so [onModerated]'s reload never touches `Dispatchers.Main` (#177). [done] names the action
     * in logs; [refusal] names a failure more precisely than [ModerationEvent.Failed]. [syncFirst] reads the server's state
     * into the cache before the event, for an action that takes the user back to a list (#1777).
     */
    fun act(
        requestId: Int,
        done: ModerationEvent,
        onSettled: (succeeded: Boolean) -> Unit = {},
        syncFirst: Boolean = false,
        refusal: (Throwable) -> ModerationEvent? = { null },
        outcome: suspend () -> ModerationEvent,
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
                    val event = outcome()
                    if (event.countsAsPositiveAction) reviewSignals.recordPositiveAction()
                    analytics.event(AnalyticsEvents.REQUEST_MODERATED, mapOf(AnalyticsEvents.PARAM_ACTION to event.actionLabel()))
                    if (syncFirst) connection.syncInto(cache, requestId)
                    eventFlow.emit(event)
                    if (!syncFirst) connection.syncInto(cache, requestId)
                }.onFailure {
                    val refused = refusal(it)
                    analytics.operationFailed("moderate_${done.actionLabel()}", it, connection, refused?.failureReason())
                    eventFlow.emit(refused ?: ModerationEvent.Failed(it.toSeerrError()))
                }
        }
        return true
    }

    /** Blocks the title alone, leaving the request as it is; the owner refreshes what it shows on success. */
    fun blockTitle(item: RequestItem) {
        if (item.id in acting.value) return
        acting.update { it + item.id }
        scope.launch(dispatcher) {
            val blocked = block(item)
            acting.update { it - item.id }
            if (blocked) onModerated()
            val event = if (blocked) ModerationEvent.Blocked else ModerationEvent.BlockFailed
            analytics.event(AnalyticsEvents.REQUEST_MODERATED, mapOf(AnalyticsEvents.PARAM_ACTION to event.actionLabel()))
            eventFlow.emit(event)
            if (blocked) connection.syncInto(cache, item.id)
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

/** The [AnalyticsEvents.PARAM_REASON] a refusal adds to its failure report, so it reads apart from a real conflict. */
private fun ModerationEvent.failureReason(): String? =
    AnalyticsEvents.REASON_NO_DEFAULT_SERVICE.takeIf { this == ModerationEvent.MediaFilesNoDefaultServer }

/**
 * Writes what the server now says into the cache every list reads, so each one updates without being told (#1665):
 * the request's own status, or its removal when the server no longer has it, and the chip counts. Best effort. It runs
 * before or after the result is reported, as the caller's `syncFirst` says.
 */
private suspend fun SeerrConnection.syncInto(
    cache: RequestStore,
    requestId: Int,
) {
    attempt { api().request(requestId) }.fold(
        onSuccess = { cache.updateServerState(requestId, it.status?.raw, it.mediaStatusOfRequestedVersion()?.raw) },
        onFailure = { if ((it as? HttpException)?.code() == HTTP_NOT_FOUND) cache.delete(requestId) },
    )
    attempt { api().requestCount() }.onSuccess {
        cache.setCounts(RequestCounts(it.total, it.pending, it.approved, it.processing, it.available))
    }
}
