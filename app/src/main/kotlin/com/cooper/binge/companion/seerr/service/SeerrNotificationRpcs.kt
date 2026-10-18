package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.AcknowledgeNotificationsRequest
import com.binge.companion.contracts.request.v1.AcknowledgeNotificationsResponse
import com.binge.companion.contracts.request.v1.ListNotificationsRequest
import com.binge.companion.contracts.request.v1.ListNotificationsResponse
import com.binge.companion.contracts.request.v1.SetNotificationForwardingRequest
import com.binge.companion.contracts.request.v1.SetNotificationForwardingResponse
import com.binge.companion.contracts.request.v1.listNotificationsResponse
import com.binge.companion.contracts.v1.ForwardedNotification
import com.cooper.binge.companion.seerr.notifications.NotificationForwarding
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import kotlinx.coroutines.flow.first

private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 100
private const val MAX_ACKNOWLEDGED = 100
private const val TOKEN_PREFIX = "n"
private const val TOKEN_PARTS = 5
private const val TOKEN_SECONDS = 1
private const val TOKEN_NANOS = 2
private const val TOKEN_SIZE = 3
private const val TOKEN_ID = 4

/**
 * The signals turned on when Binge starts forwarding and none is on yet: the user's own requests,
 * which is what Binge notified about before it forwarded anything. Without them Binge's switch would
 * read as on and deliver nothing, since every signal here defaults off. A moderator's feeds stay
 * opt-in, as they are in this app.
 */
private val FORWARDING_DEFAULT_SIGNALS =
    listOf(NotificationSignal.RequestAvailable, NotificationSignal.RequestApproved, NotificationSignal.RequestDeclined)

/**
 * `CAPABILITY_FORWARD_NOTIFICATIONS`. None of it needs a session: what is queued is still Binge's
 * to show after the session breaks, and turning forwarding on or off is Binge's switch, not the
 * server's. Which kinds are queued stays with [NotificationPrefs], this app's own settings.
 */
class SeerrNotificationRpcs(
    private val forwarding: NotificationForwarding,
    private val prefs: NotificationPrefs,
) {
    suspend fun setNotificationForwarding(request: SetNotificationForwardingRequest): SetNotificationForwardingResponse {
        if (request.enabled && !forwarding.enabled.first()) prefs.enableIfNoneOn(FORWARDING_DEFAULT_SIGNALS)
        forwarding.setEnabled(request.enabled)
        return SetNotificationForwardingResponse.getDefaultInstance()
    }

    /**
     * Pages the queue by keyset: the token carries the last notice returned, in the queue's own
     * `(created_at, id)` order, and the next page is what sorts strictly after it. Acknowledging or
     * pruning between pages cannot shift that position, so no unacknowledged notice is skipped.
     */
    suspend fun listNotifications(request: ListNotificationsRequest): ListNotificationsResponse {
        val pageSize = request.pageSize.takeIf { it > 0 }?.coerceAtMost(MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
        val after = if (request.pageToken.isEmpty()) null else decodeToken(request.pageToken, pageSize)
        val remaining = forwarding.pending().filter { after == null || it.key > after }
        val page = remaining.take(pageSize)
        return listNotificationsResponse {
            notifications += page
            if (remaining.size > page.size) nextPageToken = page.last().key.encode(pageSize)
        }
    }

    suspend fun acknowledgeNotifications(request: AcknowledgeNotificationsRequest): AcknowledgeNotificationsResponse {
        if (request.idsCount > MAX_ACKNOWLEDGED) throw invalidArgument("at most $MAX_ACKNOWLEDGED ids per call, got ${request.idsCount}")
        forwarding.acknowledge(request.idsList)
        return AcknowledgeNotificationsResponse.getDefaultInstance()
    }

    /** A token is only good with the page size it was issued under, as every paged rpc's is. */
    private fun decodeToken(
        token: String,
        pageSize: Int,
    ): NoticeKey {
        val parts = token.split(":", limit = TOKEN_PARTS).takeIf { it.size == TOKEN_PARTS && it[0] == TOKEN_PREFIX }
        val key = parts?.takeIf { it[TOKEN_SIZE].toIntOrNull() == pageSize }?.let(::noticeKeyOf)
        return key ?: throw invalidToken()
    }

    private fun invalidToken() = invalidArgument("page_token was not issued by this integration for this page_size")
}

private fun noticeKeyOf(parts: List<String>): NoticeKey? {
    val seconds = parts[TOKEN_SECONDS].toLongOrNull() ?: return null
    val nanos = parts[TOKEN_NANOS].toIntOrNull() ?: return null
    return NoticeKey(seconds, nanos, parts[TOKEN_ID])
}

/** A notice's place in the queue, ordered as the queue is: by time, then id. */
private data class NoticeKey(
    val seconds: Long,
    val nanos: Int,
    val id: String,
) : Comparable<NoticeKey> {
    override fun compareTo(other: NoticeKey): Int = compareValuesBy(this, other, NoticeKey::seconds, NoticeKey::nanos, NoticeKey::id)

    fun encode(pageSize: Int) = "$TOKEN_PREFIX:$seconds:$nanos:$pageSize:$id"
}

private val ForwardedNotification.key get() = NoticeKey(createdAt.seconds, createdAt.nanos, id)
