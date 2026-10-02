package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.accent
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoiceRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import com.binge.designsystem.R as DesR

class JobsActions(
    val onRun: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onSchedule: (id: String, cron: String) -> Unit,
)

/** One scheduled job as a settings row: run or cancel in place, the last run's outcome briefly, and a tap to reschedule where the job allows it. */
@Composable
internal fun jobRow(
    job: ServerJob,
    busy: Boolean,
    outcome: JobOutcome?,
    actions: JobsActions,
    onSchedule: () -> Unit,
): SettingsRow =
    SettingsRow(
        icon = jobIcon(job.id),
        iconTint =
            when (outcome) {
                JobOutcome.Succeeded -> BingeSentiment.Positive.fill()
                JobOutcome.Failed -> BingeSentiment.Negative.fill()
                null -> BingeSentiment.Info.fill()
            },
        label = job.name,
        detail =
            when {
                outcome == JobOutcome.Succeeded -> stringResource(R.string.server_settings_job_succeeded)
                outcome == JobOutcome.Failed -> stringResource(R.string.server_settings_job_failed)
                job.running -> stringResource(R.string.settings_job_running)
                else ->
                    formatRelativeOrAbsolute(job.nextRunMillis)?.let { stringResource(R.string.settings_job_next_run, it) }
                        ?: stringResource(R.string.settings_value_unknown)
            },
        detailColor = if (outcome == JobOutcome.Failed) BingeSentiment.Negative.accent() else null,
        trailingContent = {
            Box(
                modifier = Modifier.size(dimensionResource(R.dimen.server_settings_job_action_size)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                when {
                    outcome != null -> OutcomeIcon(outcome)
                    job.running ->
                        ExpressiveIconButton(
                            onClick = { actions.onCancel(job.id) },
                            icon = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.server_settings_job_cancel),
                            enabled = !busy,
                            loading = busy,
                        )
                    else ->
                        ExpressiveIconButton(
                            onClick = { actions.onRun(job.id) },
                            icon = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.server_settings_job_run),
                            enabled = !busy,
                            loading = busy,
                        )
                }
            }
        },
        clickable = job.schedulable,
        onClick = onSchedule,
    )

private fun jobIcon(id: String): ImageVector =
    when {
        id.contains("watchlist") -> Icons.Filled.PlaylistAddCheck
        id.contains("recently-added") -> Icons.Filled.LibraryAdd
        id.contains("full-scan") -> Icons.Filled.VideoLibrary
        id.startsWith("radarr") -> Icons.Filled.Movie
        id.startsWith("sonarr") -> Icons.Filled.Tv
        id.contains("reset") -> Icons.Filled.RestartAlt
        id.startsWith("download") -> Icons.Filled.CloudDownload
        id.startsWith("availability") -> Icons.Filled.Sync
        id.contains("image") -> Icons.Filled.Image
        else -> Icons.Filled.Schedule
    }

@Composable
private fun OutcomeIcon(outcome: JobOutcome) {
    val ok = outcome == JobOutcome.Succeeded
    Icon(
        imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
        contentDescription = stringResource(if (ok) R.string.server_settings_job_succeeded else R.string.server_settings_job_failed),
        tint = (if (ok) BingeSentiment.Positive else BingeSentiment.Negative).fill(),
    )
}

/** A preset, or a cron of the admin's own; the field shows whichever was picked last. */
@Composable
internal fun ScheduleDialog(
    job: ServerJob,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var cron by rememberSaveable { mutableStateOf("") }
    val presets = job.presets()
    BingeConfirmDialog(
        title = stringResource(R.string.server_settings_job_schedule_title, job.name),
        message = stringResource(R.string.server_settings_job_schedule_message),
        confirmLabel = stringResource(R.string.user_settings_save),
        onConfirm = { cron.trim().takeIf { it.isNotBlank() }?.let(onConfirm) },
        onDismiss = onDismiss,
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                ChoiceRow(
                    title = stringResource(R.string.server_settings_job_presets),
                    choices = presets.map { it.cron to it.label() },
                    selected = cron.takeIf { c -> presets.any { it.cron == c } },
                    onSelect = { cron = it },
                )
                EditorTextField(
                    cron,
                    stringResource(R.string.server_settings_job_cron),
                    supporting = stringResource(R.string.server_settings_job_cron_hint),
                    imeAction = ImeAction.Done,
                ) { cron = it }
            }
        },
    )
}

@Composable
private fun SchedulePreset.label(): String =
    when (unit) {
        ScheduleUnit.Minutes -> pluralStringResource(R.plurals.server_settings_every_minutes, every, every)
        ScheduleUnit.Hours -> pluralStringResource(R.plurals.server_settings_every_hours, every, every)
        ScheduleUnit.Days -> pluralStringResource(R.plurals.server_settings_every_days, every, every)
    }
