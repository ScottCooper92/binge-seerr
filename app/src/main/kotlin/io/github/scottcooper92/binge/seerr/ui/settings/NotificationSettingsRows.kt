package io.github.scottcooper92.binge.seerr.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.notifications.NotificationSignal

/**
 * The poll's group: a toggle per signal the viewer may use, the blocked state where the system
 * would show nothing, and when the poll ran and runs next. Turning the first signal on asks for
 * the notification permission on 13+; what the prompt or the system page decided is re-read on resume.
 */
@Composable
internal fun notificationRows(
    settings: NotificationSettings,
    actions: SettingsActions,
): List<SettingsRow> {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.onNotificationAccessChanged() }
    val askPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { actions.onNotificationAccessChanged() }
    val toggles =
        settings.offered.map { signal ->
            val on = signal in settings.enabled
            SettingsRow(
                icon = signal.icon(),
                iconTint = BingeSentiment.Info.fill(),
                label = stringResource(signal.labelRes()),
                detail = stringResource(signal.captionRes()),
                trailingContent = { Switch(checked = on, onCheckedChange = null) },
                onClick = {
                    if (!on &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    actions.onToggleSignal(signal, !on)
                },
            )
        }
    val blocked =
        if (settings.blocked && settings.enabled.isNotEmpty()) {
            SettingsRow(
                icon = Icons.Filled.NotificationsOff,
                iconTint = BingeSentiment.Negative.fill(),
                label = stringResource(R.string.settings_notifications_blocked),
                detail = stringResource(R.string.settings_notifications_blocked_caption),
                detailColor = BingeSentiment.Negative.fill(),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )
        } else {
            null
        }
    val schedule =
        if (settings.enabled.isNotEmpty()) {
            SettingsRow(
                icon = Icons.Filled.Schedule,
                iconTint = BingeSentiment.Neutral.fill(),
                label = stringResource(R.string.settings_notifications_poll),
                detail =
                    listOfNotNull(
                        formatRelativeOrAbsolute(
                            settings.lastRunMillis,
                        )?.let { stringResource(R.string.settings_notifications_last_run, it) },
                        formatRelativeOrAbsolute(settings.nextRunMillis)?.let { stringResource(R.string.settings_job_next_run, it) },
                    ).joinToString(
                        stringResource(R.string.hub_meta_separator),
                    ).ifEmpty { stringResource(R.string.settings_notifications_not_yet) },
                clickable = false,
            )
        } else {
            null
        }
    return toggles + listOfNotNull(blocked, schedule)
}

private fun NotificationSignal.icon(): ImageVector =
    when (this) {
        NotificationSignal.PendingRequests -> Icons.Filled.Inbox
        NotificationSignal.OpenIssues -> Icons.Filled.Report
        NotificationSignal.RequestAvailable -> Icons.Filled.CheckCircle
        NotificationSignal.RequestApproved -> Icons.Filled.ThumbUp
        NotificationSignal.RequestDeclined -> Icons.Filled.ThumbDown
    }

@StringRes
private fun NotificationSignal.labelRes(): Int =
    when (this) {
        NotificationSignal.PendingRequests -> R.string.settings_signal_pending
        NotificationSignal.OpenIssues -> R.string.settings_signal_issues
        NotificationSignal.RequestAvailable -> R.string.settings_signal_available
        NotificationSignal.RequestApproved -> R.string.settings_signal_approved
        NotificationSignal.RequestDeclined -> R.string.settings_signal_declined
    }

@StringRes
private fun NotificationSignal.captionRes(): Int =
    when (this) {
        NotificationSignal.PendingRequests -> R.string.settings_signal_pending_caption
        NotificationSignal.OpenIssues -> R.string.settings_signal_issues_caption
        NotificationSignal.RequestAvailable -> R.string.settings_signal_available_caption
        NotificationSignal.RequestApproved -> R.string.settings_signal_approved_caption
        NotificationSignal.RequestDeclined -> R.string.settings_signal_declined_caption
    }
