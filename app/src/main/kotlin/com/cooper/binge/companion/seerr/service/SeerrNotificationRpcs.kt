package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.AcknowledgeNotificationsRequest
import com.binge.companion.contracts.request.v1.AcknowledgeNotificationsResponse
import com.binge.companion.contracts.request.v1.ListNotificationsRequest
import com.binge.companion.contracts.request.v1.ListNotificationsResponse
import com.binge.companion.contracts.request.v1.SetNotificationForwardingRequest
import com.binge.companion.contracts.request.v1.SetNotificationForwardingResponse
import com.binge.companion.contracts.request.v1.listNotificationsResponse
import com.cooper.binge.companion.seerr.notifications.NotificationForwarding
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import kotlinx.coroutines.flow.first

private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 100
private const val MAX_ACKNOWLEDGED = 100
private const val TOKEN_PREFIX = "n"
private const val TOKEN_PARTS = 3

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
     * Pages the queue by offset. Binge reads every page before it acknowledges, and new notices join
     * the end, so an offset stays put while it pages; a notice dropped meanwhile can only make one
     * show twice, which the id dedupes.
     */
    suspend fun listNotifications(request: ListNotificationsRequest): ListNotificationsResponse {
        val pageSize = request.pageSize.takeIf { it > 0 }?.coerceAtMost(MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
        val skip = if (request.pageToken.isEmpty()) 0 else decodeToken(request.pageToken, pageSize)
        val queue = forwarding.pending()
        val page = queue.drop(skip).take(pageSize)
        val next = skip + page.size
        return listNotificationsResponse {
            notifications += page
            if (next < queue.size) nextPageToken = "$TOKEN_PREFIX:$next:$pageSize"
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
    ): Int {
        val (prefix, skip, issuedSize) =
            token.split(":").takeIf { it.size == TOKEN_PARTS } ?: throw invalidToken()
        val offset = skip.toIntOrNull()?.takeIf { it >= 0 }
        if (prefix != TOKEN_PREFIX || issuedSize.toIntOrNull() != pageSize || offset == null) throw invalidToken()
        return offset
    }

    private fun invalidToken() = invalidArgument("page_token was not issued by this integration for this page_size")
}
