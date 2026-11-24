package com.cooper.binge.companion.seerr.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import com.cooper.homelab.core.uiandroid.notifications.backgroundAccessRow
import com.cooper.homelab.core.uiandroid.notifications.notificationsBlockedRow
import com.cooper.homelab.core.uiandroid.notifications.pollIntervalRow

/**
 * The poll's group: a toggle per signal the viewer may use, a note while Binge shows them instead,
 * the blocked state where the system would show nothing, how often it runs, and when the poll ran and runs next, then a row to the app's
 * own settings while Android holds its background work back. Turning the first signal on asks for
 * the notification permission on 13+; what the prompt or the system page decided is re-read on resume.
 */
@Composable
internal fun notificationRows(
    settings: NotificationSettings,
    onToggleSignal: (NotificationSignal, Boolean) -> Unit,
    onAccessChanged: () -> Unit,
    onOpenInterval: () -> Unit,
    /** What the poll's last and next runs are worded against (#989). */
    now: Long,
): List<ListItem> {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onAccessChanged() }
    val askPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAccessChanged() }
    val polling = settings.enabled.isNotEmpty()
    return signalToggles(settings, askPermission::launch, onToggleSignal) +
        listOfNotNull(
            forwardedRow(settings),
            notificationsBlockedRow(searchKey = null).takeIf { settings.blocked && polling },
            pollIntervalRow(settings.intervalMinutes, onOpenInterval, searchKey = null).takeIf { polling },
            scheduleRow(settings, now).takeIf { polling },
            backgroundAccessRow(searchKey = null).takeIf { polling && !settings.unrestricted },
        )
}

/** A toggle per signal on offer; turning the first on asks for the notification permission on 13+. */
@Composable
private fun signalToggles(
    settings: NotificationSettings,
    askPermission: (String) -> Unit,
    onToggleSignal: (NotificationSignal, Boolean) -> Unit,
): List<ListItem> =
    settings.offered.map { signal ->
        val on = signal in settings.enabled
        ListItem(
            icon = signal.icon(),
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(signal.labelRes()),
            toggled = on,
            onClick = {
                if (!on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askPermission(Manifest.permission.POST_NOTIFICATIONS)
                onToggleSignal(signal, !on)
            },
        )
    }

/** A note, only while Binge shows these notices instead of this app. */
@Composable
private fun forwardedRow(settings: NotificationSettings): ListItem? =
    ListItem(
        icon = Icons.AutoMirrored.Filled.Forward,
        iconTint = BingeSentiment.Info.fill(),
        label = stringResource(R.string.settings_notifications_forwarded),
        detail = stringResource(R.string.settings_notifications_forwarded_caption),
        supportingMaxLines = 3,
        clickable = false,
    ).takeIf { settings.forwardedToBinge }

/** When the poll last ran and runs next, which is what shows it running late. */
@Composable
private fun scheduleRow(
    settings: NotificationSettings,
    now: Long,
): ListItem =
    ListItem(
        icon = Icons.Filled.Schedule,
        iconTint = BingeSentiment.Neutral.fill(),
        label = stringResource(R.string.settings_notifications_poll),
        detail =
            listOfNotNull(
                formatRelativeOrAbsolute(settings.lastRunMillis, now)?.let { stringResource(R.string.settings_notifications_last_run, it) },
                formatRelativeOrAbsolute(settings.nextRunMillis, now)?.let { stringResource(R.string.settings_job_next_run, it) },
            ).joinToString(stringResource(R.string.hub_meta_separator))
                .ifEmpty { stringResource(R.string.settings_notifications_not_yet) },
        clickable = false,
    )

private fun NotificationSignal.icon(): ImageVector =
    when (this) {
        NotificationSignal.PendingRequests -> Icons.Filled.Inbox
        NotificationSignal.OpenIssues -> Icons.Filled.Report
        NotificationSignal.RequestAvailable -> Icons.Filled.CheckCircle
        NotificationSignal.RequestApproved -> Icons.Filled.ThumbUp
        NotificationSignal.RequestDeclined -> Icons.Filled.ThumbDown
    }

private val INTERVAL_LABELS: Map<Int, Int> =
    mapOf(
        15 to R.string.settings_notifications_interval_15,
        30 to R.string.settings_notifications_interval_30,
        60 to R.string.settings_notifications_interval_60,
        120 to R.string.settings_notifications_interval_120,
        360 to R.string.settings_notifications_interval_360,
    )

/** The words for one of the poll's intervals; a value the settings do not offer reads as the default. */
@StringRes
internal fun Int.intervalLabelRes(): Int = INTERVAL_LABELS[this] ?: R.string.settings_notifications_interval_15

@StringRes
private fun NotificationSignal.labelRes(): Int =
    when (this) {
        NotificationSignal.PendingRequests -> R.string.settings_signal_pending
        NotificationSignal.OpenIssues -> R.string.settings_signal_issues
        NotificationSignal.RequestAvailable -> R.string.settings_signal_available
        NotificationSignal.RequestApproved -> R.string.settings_signal_approved
        NotificationSignal.RequestDeclined -> R.string.settings_signal_declined
    }
