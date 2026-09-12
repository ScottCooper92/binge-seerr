package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrAddToBlocklistBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot feedback for a moderation; the partial cases say the action landed even though the block did not. */
sealed interface ModerationEvent {
    data object Approved : ModerationEvent

    data object Retried : ModerationEvent

    data object Declined : ModerationEvent

    data object DeclinedAndBlocked : ModerationEvent

    data object DeclinedButBlockFailed : ModerationEvent

    data object Removed : ModerationEvent

    data object RemovedAndBlocked : ModerationEvent

    data object RemovedButBlockFailed : ModerationEvent

    data class Failed(
        val error: SeerrError,
    ) : ModerationEvent
}

/**
 * The write side of a request: approve, decline, retry, remove, each optionally blocking the
 * title after a decline or removal. The server keeps a request when a title is blocked, which is
 * why blocking only rides another action. Owns the per-request acting set and the feedback
 * events; [onModerated] fires after a success so the owner can refresh what it shows.
 */
class RequestModeration(
    private val scope: CoroutineScope,
    private val connection: SeerrConnection,
    private val onModerated: () -> Unit,
) {
    private val acting = MutableStateFlow<Set<Int>>(emptySet())
    val actingIds: StateFlow<Set<Int>> = acting.asStateFlow()

    private val eventFlow = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ModerationEvent> = eventFlow.asSharedFlow()

    fun approve(requestId: Int) = moderate(requestId, ModerationEvent.Approved) { connection.api().approveRequest(it) }

    fun retry(requestId: Int) = moderate(requestId, ModerationEvent.Retried) { connection.api().retryRequest(it) }

    fun decline(
        item: RequestItem,
        blockTitle: Boolean,
    ) = actThenMaybeBlock(
        item,
        blockTitle,
        ModerationEvent.Declined,
        ModerationEvent.DeclinedAndBlocked,
        ModerationEvent.DeclinedButBlockFailed,
    ) { connection.api().declineRequest(it) }

    fun remove(
        item: RequestItem,
        blockTitle: Boolean,
    ) = actThenMaybeBlock(
        item,
        blockTitle,
        ModerationEvent.Removed,
        ModerationEvent.RemovedAndBlocked,
        ModerationEvent.RemovedButBlockFailed,
    ) { connection.api().deleteRequest(it) }

    private fun moderate(
        requestId: Int,
        success: ModerationEvent,
        action: suspend (Int) -> Unit,
    ) = actThenMaybeBlock(null, requestId, false, success, success, success, action)

    private fun actThenMaybeBlock(
        item: RequestItem,
        blockTitle: Boolean,
        done: ModerationEvent,
        doneAndBlocked: ModerationEvent,
        blockFailed: ModerationEvent,
        action: suspend (Int) -> Unit,
    ) = actThenMaybeBlock(item, item.id, blockTitle, done, doneAndBlocked, blockFailed, action)

    /**
     * Acting is raised before the launch so a second tap is swallowed at once, and cleared as soon
     * as the action settles, before the refresh and the longer block follow-up.
     */
    private fun actThenMaybeBlock(
        item: RequestItem?,
        requestId: Int,
        blockTitle: Boolean,
        done: ModerationEvent,
        doneAndBlocked: ModerationEvent,
        blockFailed: ModerationEvent,
        action: suspend (Int) -> Unit,
    ) {
        if (requestId in acting.value) return
        acting.update { it + requestId }
        scope.launch {
            val result = runCatching { action(requestId) }
            acting.update { it - requestId }
            result
                .onSuccess {
                    onModerated()
                    val event =
                        if (blockTitle && item != null) {
                            if (block(item)) doneAndBlocked else blockFailed
                        } else {
                            done
                        }
                    eventFlow.emit(event)
                }.onFailure { eventFlow.emit(ModerationEvent.Failed(it.toSeerrError())) }
        }
    }

    private suspend fun block(item: RequestItem): Boolean =
        runCatching {
            val mediaType = if (item.mediaType == RequestMediaType.Movie) "movie" else "tv"
            connection.api().addToBlocklist(
                connection.profile().blocklistPath,
                SeerrAddToBlocklistBody(item.tmdbId, mediaType, item.title.orEmpty()),
            )
        }.isSuccess
}
