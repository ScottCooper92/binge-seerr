package io.github.scottcooper92.binge.seerr.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.DeepLinks
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem

private const val CONNECTION_NOTIFICATION_ID = 4203

/**
 * Posts what the poll found. A batch posts under its signal's tag with the batch's newest id, so
 * it adds to an earlier untapped batch rather than replacing it; the system bundles them once
 * several pile up. A batch of one opens its page; a larger one, the list it belongs to.
 */
class AndroidNotifier(
    private val context: Context,
) : SeerrNotifier {
    /**
     * App-level notifications on, plus the runtime permission on 13+. A muted channel is not
     * checked: the feeds can be muted while the connection channel stays on, and the system drops
     * a muted channel's posts itself.
     */
    override fun canPost(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun notifyNewRequests(items: List<RequestItem>) =
        notifyBatch(NotificationSignal.PendingRequests, items.map { it.id }, items.mapNotNull { it.title })

    override fun notifyNewIssues(items: List<IssueItem>) =
        notifyBatch(NotificationSignal.OpenIssues, items.map { it.id }, items.mapNotNull { it.title })

    override fun notifyRequestsAvailable(items: List<RequestItem>) =
        notifyBatch(NotificationSignal.RequestAvailable, items.map { it.id }, items.mapNotNull { it.title })

    override fun notifyRequestsApproved(items: List<RequestItem>) =
        notifyBatch(NotificationSignal.RequestApproved, items.map { it.id }, items.mapNotNull { it.title })

    override fun notifyRequestsDeclined(items: List<RequestItem>) =
        notifyBatch(NotificationSignal.RequestDeclined, items.map { it.id }, items.mapNotNull { it.title })

    override fun notifyConnectionProblem() =
        post(
            id = CONNECTION_NOTIFICATION_ID,
            tag = null,
            channel = NotificationChannelKind.Connection,
            title = context.getString(R.string.notif_connection_title),
            body = context.getString(R.string.notif_connection_body),
            link = DeepLinks.reconnect(),
        )

    override fun cancelConnectionProblem() = NotificationManagerCompat.from(context).cancel(CONNECTION_NOTIFICATION_ID)

    override fun cancelActivity() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val tags = NotificationSignal.entries.mapTo(mutableSetOf()) { it.key }
        manager.activeNotifications.filter { it.tag in tags }.forEach { manager.cancel(it.tag, it.id) }
    }

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
        post(
            id = batchId,
            tag = signal.key,
            channel = signal.channel,
            title = context.resources.getQuantityString(signal.titleRes, ids.size, ids.size),
            body = body,
            link = signal.deepLink(ids),
        )
    }

    /** [canPost] guards the permission, but lint follows only a check inlined in the same method. */
    @SuppressLint("MissingPermission")
    private fun post(
        id: Int,
        tag: String?,
        channel: NotificationChannelKind,
        title: String,
        body: String?,
        link: String,
    ) {
        if (!canPost()) return
        ensureChannel(channel)
        val builder =
            NotificationCompat
                .Builder(context, channel.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setPriority(channel.priority)
                .setAutoCancel(true)
                .setContentIntent(openApp(id, link))
        if (body != null) builder.setContentText(body)
        NotificationManagerCompat.from(context).notify(tag, id, builder.build())
    }

    /** The app's launch intent carrying the link; the notification id doubles as the request code, so each keeps its own. */
    private fun openApp(
        id: Int,
        link: String,
    ): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply { data = link.toUri() } ?: return null
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ensureChannel(channel: NotificationChannelKind) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channel.id) != null) return
        val created =
            NotificationChannel(channel.id, context.getString(channel.nameRes), channel.importance).apply {
                description = context.getString(channel.descriptionRes)
            }
        manager.createNotificationChannel(created)
    }
}
