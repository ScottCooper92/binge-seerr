package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

class JobsActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRun: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onSchedule: (id: String, cron: String) -> Unit,
)

/** The jobs page: each job with its next run, run or cancelled in place, and a schedule picked from the presets or typed. */
@Composable
fun JobsScreen(
    state: JobsUiState,
    events: Flow<EditorEvent>,
    actions: JobsActions,
) {
    ServerActionPage(title = stringResource(R.string.server_settings_jobs), events = events, onBack = actions.onBack) {
        when (state) {
            JobsUiState.Loading -> LoadingScreen()
            is JobsUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
            is JobsUiState.Ready ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(dimensionResource(DesR.dimen.screen_content_inset)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
                ) {
                    state.jobs.forEach { job -> JobRow(job, busy = job.id in state.busyIds, actions) }
                    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
                }
        }
    }
}

@Composable
private fun JobRow(
    job: ServerJob,
    busy: Boolean,
    actions: JobsActions,
) {
    var scheduling by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(job.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            when {
                job.running -> stringResource(R.string.settings_job_running)
                else ->
                    formatRelativeOrAbsolute(job.nextRunMillis)?.let { stringResource(R.string.settings_job_next_run, it) }
                        ?: stringResource(R.string.settings_value_unknown)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            if (job.running) {
                BingeTextButton(label = stringResource(R.string.server_settings_job_cancel), onClick = {
                    actions.onCancel(job.id)
                }, enabled = !busy, loading = busy)
            } else {
                BingeTextButton(label = stringResource(R.string.server_settings_job_run), onClick = {
                    actions.onRun(job.id)
                }, enabled = !busy, loading = busy)
            }
            if (job.schedulable) {
                BingeTextButton(
                    label = stringResource(R.string.server_settings_job_schedule),
                    onClick = { scheduling = true },
                    enabled = !busy,
                )
            }
        }
    }
    if (scheduling) {
        ScheduleDialog(
            job = job,
            onConfirm = { cron ->
                scheduling = false
                actions.onSchedule(job.id, cron)
            },
            onDismiss = { scheduling = false },
        )
    }
}

/** A preset, or a cron of the admin's own; the field shows whichever was picked last. */
@Composable
private fun ScheduleDialog(
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
                ChoicePicker(
                    title = stringResource(R.string.server_settings_job_presets),
                    choices = presets.map { it.cron to it.label() },
                    selected = cron.takeIf { c -> presets.any { it.cron == c } },
                    onSelect = { cron = it },
                )
                EditorTextField(
                    cron,
                    stringResource(R.string.server_settings_job_cron),
                    supporting = stringResource(R.string.server_settings_job_cron_hint),
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
