package com.cooper.binge.companion.seerr.notifications

import android.content.Context
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.DeepLinks
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.homelab.core.notifications.NotificationContent
import com.cooper.homelab.core.notifications.NotificationPoster

private const val CONNECTION_NOTIFICATION_ID = 4203

/**
 * Posts what the poll found, through the shared library's poster. A batch posts under its signal's tag with the batch's
 * newest id, so it adds to an earlier untapped batch rather than replacing it; the system bundles them once several pile up.
 * A batch of one opens its page; a larger one, the list it belongs to.
 */
class AndroidNotifier(
    private val context: Context,
    private val poster: NotificationPoster = NotificationPoster(context, R.drawable.ic_notification),
) : SeerrNotifier {
    /**
     * App-level notifications on, plus the runtime permission on 13+. A muted channel is not
     * checked: the feeds can be muted while the connection channel stays on, and the system drops
     * a muted channel's posts itself.
     */
    override suspend fun canPost(): Boolean = poster.mayPost()

    override suspend fun notifyNewRequests(items: List<RequestItem>) =
        notifyBatch(NotificationSignal.PendingRequests, items.map { it.id }, items.mapNotNull { it.title })

    override suspend fun notifyNewIssues(items: List<IssueItem>) =
        notifyBatch(NotificationSignal.OpenIssues, items.map { it.id }, items.mapNotNull { it.title })

    override suspend fun notifyRequestsAvailable(items: List<RequestItem>) =
        notifyBatch(NotificationSignal.RequestAvailable, items.map { it.id }, items.mapNotNull { it.title })

    override suspend fun notifyRequestsApproved(items: List<RequestItem>) =
        notifyBatch(NotificationSignal.RequestApproved, items.map { it.id }, items.mapNotNull { it.title })

    override suspend fun notifyRequestsDeclined(items: List<RequestItem>) =
        notifyBatch(NotificationSignal.RequestDeclined, items.map { it.id }, items.mapNotNull { it.title })

    override suspend fun notifyConnectionProblem() {
        poster.post(
            id = CONNECTION_NOTIFICATION_ID,
            channel = NotificationChannelKind.Connection.spec,
            content =
                NotificationContent(
                    title = context.getString(R.string.notif_connection_title),
                    body = context.getString(R.string.notif_connection_body),
                    link = DeepLinks.reconnect(),
                    priority = NotificationChannelKind.Connection.priority,
                ),
        )
    }

    override suspend fun cancelConnectionProblem() = poster.cancel(CONNECTION_NOTIFICATION_ID)

    override fun cancelActivity() = poster.cancelTagged(NotificationSignal.entries.mapTo(mutableSetOf()) { it.key })

    private fun notifyBatch(
        signal: NotificationSignal,
        ids: List<Int>,
        titles: List<String>,
    ) {
        val batchId = ids.maxOrNull() ?: return
        val body =
            summaryLine(titles, ids.size) { first, others ->
                context.resources.getQuantityString(R.plurals.notif_and_more, others, first, others)
            }
        poster.post(
            id = batchId,
            channel = signal.channel.spec,
            content =
                NotificationContent(
                    title = context.resources.getQuantityString(signal.titleRes, ids.size, ids.size),
                    body = body,
                    tag = signal.key,
                    link = signal.deepLink(ids),
                    priority = signal.channel.priority,
                ),
        )
    }
}
