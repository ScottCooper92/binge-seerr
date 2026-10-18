package com.cooper.binge.companion.seerr.notifications

import com.binge.companion.contracts.v1.ForwardedNotification
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.forwardedNotification
import com.binge.companion.contracts.v1.mediaId
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.google.protobuf.timestamp

/** The `kind` a forwarded connection notice carries; a signal's carries its own key. */
internal const val FORWARDED_KIND_CONNECTION = "connection"

/**
 * Routes each notice: queued for Binge while it forwards this app's notices
 * ([NotificationForwarding.isActive]), posted by [local] otherwise, never both. Binge's switch decides
 * who shows them; this app's own settings still decide which kinds there are. A batch of one names
 * its title, so a tap in Binge opens it; a larger one names none, and opens this app's page there.
 */
class ForwardingNotifier(
    private val local: SeerrNotifier,
    private val forwarding: NotificationForwarding,
    private val words: NotificationText,
    private val clock: () -> Long = System::currentTimeMillis,
) : SeerrNotifier {
    override suspend fun canPost(): Boolean = forwarding.isActive() || local.canPost()

    override suspend fun notifyNewRequests(items: List<RequestItem>) =
        route(NotificationSignal.PendingRequests, items.requestBatch()) { local.notifyNewRequests(items) }

    override suspend fun notifyNewIssues(items: List<IssueItem>) =
        route(NotificationSignal.OpenIssues, items.issueBatch()) { local.notifyNewIssues(items) }

    override suspend fun notifyRequestsAvailable(items: List<RequestItem>) =
        route(NotificationSignal.RequestAvailable, items.requestBatch()) { local.notifyRequestsAvailable(items) }

    override suspend fun notifyRequestsApproved(items: List<RequestItem>) =
        route(NotificationSignal.RequestApproved, items.requestBatch()) { local.notifyRequestsApproved(items) }

    override suspend fun notifyRequestsDeclined(items: List<RequestItem>) =
        route(NotificationSignal.RequestDeclined, items.requestBatch()) { local.notifyRequestsDeclined(items) }

    override suspend fun notifyConnectionProblem() {
        if (!forwarding.isActive()) return local.notifyConnectionProblem()
        val now = clock()
        forwarding.enqueue(
            forwardedNotification {
                id = "$FORWARDED_KIND_CONNECTION:$now"
                title = words.connectionTitle()
                text = words.connectionBody()
                kind = FORWARDED_KIND_CONNECTION
                createdAt = now.toTimestamp()
            },
        )
    }

    /** A notice already in Binge is Binge's to clear; these clear what this app posted itself. */
    override fun cancelConnectionProblem() = local.cancelConnectionProblem()

    override fun cancelActivity() = local.cancelActivity()

    private suspend fun route(
        signal: NotificationSignal,
        batch: Batch,
        post: suspend () -> Unit,
    ) {
        if (!forwarding.isActive()) return post()
        forwarding.enqueue(batch.toNotification(signal))
    }

    private fun Batch.toNotification(signal: NotificationSignal): ForwardedNotification {
        val now = clock()
        val batch = this
        return forwardedNotification {
            // The batch's newest id names it, as the posted notice's id does; the time keeps a later
            // batch that happens to share it, a request re-entering a state, from reading as a repeat.
            id = "${signal.key}:${batch.ids.max()}:$now"
            title = words.title(signal, batch.ids.size)
            words.body(batch.titles, batch.ids.size)?.let { text = it }
            batch.only?.let { media = it }
            kind = signal.key
            createdAt = now.toTimestamp()
        }
    }

    /** What a notice needs from a batch: the ids it counts, the titles it names, and the title of a batch of one. */
    private class Batch(
        val ids: List<Int>,
        val titles: List<String>,
        val only: MediaId?,
    )

    private fun List<RequestItem>.requestBatch() =
        Batch(map { it.id }, mapNotNull { it.title }, singleOrNull()?.let { mediaIdOf(it.mediaType, it.tmdbId) })

    private fun List<IssueItem>.issueBatch() =
        Batch(map { it.id }, mapNotNull { it.title }, singleOrNull()?.let { mediaIdOf(it.mediaType, it.tmdbId) })
}

private fun mediaIdOf(
    type: RequestMediaType,
    tmdb: Int,
): MediaId? =
    if (tmdb <= 0) {
        null
    } else {
        mediaId {
            mediaType = if (type == RequestMediaType.Tv) MediaType.MEDIA_TYPE_TV else MediaType.MEDIA_TYPE_MOVIE
            tmdbId = tmdb
        }
    }

private fun Long.toTimestamp() =
    timestamp {
        seconds = this@toTimestamp / MILLIS_PER_SECOND
        nanos = ((this@toTimestamp % MILLIS_PER_SECOND) * NANOS_PER_MILLI).toInt()
    }

private const val MILLIS_PER_SECOND = 1000L
private const val NANOS_PER_MILLI = 1_000_000L
