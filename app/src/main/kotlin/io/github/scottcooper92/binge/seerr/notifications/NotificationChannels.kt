package io.github.scottcooper92.binge.seerr.notifications

import android.app.NotificationManager
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import io.github.scottcooper92.binge.seerr.R

/**
 * The channels: one per kind of activity, so a moderator can mute the feeds and keep their own
 * requests, and a separate high-importance one for the connection, which is actionable.
 */
enum class NotificationChannelKind(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val importance: Int,
    val priority: Int,
) {
    PendingRequests(
        id = "pending_requests",
        nameRes = R.string.notif_channel_requests_name,
        descriptionRes = R.string.notif_channel_requests_description,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
    ),
    OpenIssues(
        id = "open_issues",
        nameRes = R.string.notif_channel_issues_name,
        descriptionRes = R.string.notif_channel_issues_description,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
    ),
    OwnRequests(
        id = "own_requests",
        nameRes = R.string.notif_channel_own_name,
        descriptionRes = R.string.notif_channel_own_description,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
    ),
    Connection(
        id = "connection",
        nameRes = R.string.notif_channel_connection_name,
        descriptionRes = R.string.notif_channel_connection_description,
        importance = NotificationManager.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
    ),
}

/** The channel a signal posts on. */
val NotificationSignal.channel: NotificationChannelKind
    get() =
        when (this) {
            NotificationSignal.PendingRequests -> NotificationChannelKind.PendingRequests
            NotificationSignal.OpenIssues -> NotificationChannelKind.OpenIssues
            NotificationSignal.RequestAvailable,
            NotificationSignal.RequestApproved,
            NotificationSignal.RequestDeclined,
            -> NotificationChannelKind.OwnRequests
        }

/** A batch's title, counting what arrived. */
@get:PluralsRes
val NotificationSignal.titleRes: Int
    get() =
        when (this) {
            NotificationSignal.PendingRequests -> R.plurals.notif_new_requests
            NotificationSignal.OpenIssues -> R.plurals.notif_new_issues
            NotificationSignal.RequestAvailable -> R.plurals.notif_requests_available
            NotificationSignal.RequestApproved -> R.plurals.notif_requests_approved
            NotificationSignal.RequestDeclined -> R.plurals.notif_requests_declined
        }

/** "Heat" for one, "Heat and 2 more" for several, null when no title resolved and the count stands alone. */
fun summaryLine(
    titles: List<String>,
    total: Int,
    andMore: (first: String, others: Int) -> String,
): String? = titles.firstOrNull()?.let { first -> if (total <= 1) first else andMore(first, total - 1) }
