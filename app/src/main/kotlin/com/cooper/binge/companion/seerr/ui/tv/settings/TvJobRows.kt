package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.labelRes
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.ServerJob
import com.cooper.binge.companion.seerr.ui.settings.server.mediaServerScanJobId
import kotlinx.coroutines.delay

/** What a television can do to a scheduled job: start it, or stop it while it runs. */
internal class TvJobActions(
    val onRun: (id: String) -> Unit,
    val onCancel: (id: String) -> Unit,
) {
    companion object {
        val None = TvJobActions(onRun = {}, onCancel = {})
    }
}

/** What the sheet shows of the jobs: the list's state, what can be done to a job, and which the user has pressed. */
internal class TvJobs(
    val state: JobsUiState,
    val actions: TvJobActions,
    val presses: TvJobPresses = TvJobPresses(),
)

/**
 * A press on a job's row: whether it stopped the job, and whether the row still says it is working. [serial] counts the
 * presses on that row, so a second press while the first is held is a new value and restarts the hold.
 */
internal data class TvJobPress(
    val stopping: Boolean,
    val holding: Boolean = true,
    val serial: Int = 1,
)

/**
 * The jobs pressed since the sheet opened. A press says the job is working for at least [JOB_MIN_WORKING_MILLIS], so a
 * job the server finishes in a moment still shows that it ran, and then what came of it until the sheet closes.
 */
@Stable
internal class TvJobPresses {
    private val presses = mutableStateMapOf<String, TvJobPress>()

    operator fun get(id: String): TvJobPress? = presses[id]

    fun press(
        id: String,
        stopping: Boolean,
    ) {
        presses[id] = TvJobPress(stopping, serial = (presses[id]?.serial ?: 0) + 1)
    }

    fun release(id: String) {
        presses[id]?.let { presses[id] = it.copy(holding = false) }
    }
}

/** The shortest time a pressed job's row says it is working. */
internal const val JOB_MIN_WORKING_MILLIS = 900L

/** What a job's row says, in order of precedence. */
internal enum class TvJobLine { Failed, Working, Running, Stopped, Done, Schedule }

/**
 * A job's line: a refusal first, then working while a call is out or a press is held, then running for as long as the job
 * runs, then what the last press did, and otherwise when it next runs.
 */
internal fun jobLineOf(
    running: Boolean,
    status: TvJobStatus,
    press: TvJobPress?,
): TvJobLine =
    when {
        status == TvJobStatus.Failed -> TvJobLine.Failed
        status == TvJobStatus.Busy || press?.holding == true -> TvJobLine.Working
        running -> TvJobLine.Running
        press?.stopping == true -> TvJobLine.Stopped
        press != null -> TvJobLine.Done
        else -> TvJobLine.Schedule
    }

/** Where a job's last action stands, as its row shows it. */
internal enum class TvJobStatus { Idle, Busy, Failed }

/** The status of [id] in [this]: busy while a call is in flight, failed once one was refused, else idle. A list not read says nothing of a job. */
internal fun JobsUiState.statusOf(id: String): TvJobStatus =
    when (this) {
        is JobsUiState.Ready ->
            when (id) {
                in busyIds -> TvJobStatus.Busy
                in failedIds -> TvJobStatus.Failed
                else -> TvJobStatus.Idle
            }
        JobsUiState.Loading, is JobsUiState.Error -> TvJobStatus.Idle
    }

internal fun JobsUiState.jobOrNull(id: String): ServerJob? = (this as? JobsUiState.Ready)?.jobs?.firstOrNull { it.id == id }

/**
 * A job's line, in place: that a call is on its way, that it was refused, else whether the job is running or when it next
 * runs. The first two stay until the job's next attempt rather than passing as a timed note.
 */
@Composable
internal fun jobLine(
    job: ServerJob?,
    status: TvJobStatus,
    now: Long,
): String? =
    when (status) {
        TvJobStatus.Busy -> stringResource(R.string.tv_settings_job_working)
        TvJobStatus.Failed -> stringResource(R.string.tv_settings_job_failed_short)
        TvJobStatus.Idle -> job?.let { jobStatusText(it.running, it.nextRunMillis, now) }
    }

/**
 * The media server row: start the library scan, the "it downloaded but isn't showing" fix from the sofa, or cancel it
 * while it runs. Its line is the scan's own state in place, else the server it scans. It takes no press hold: a scan
 * runs for minutes, so its own running line already shows the press took, where a short job would be gone in a blink.
 */
@Composable
internal fun mediaServerRow(
    server: ServerSummary,
    jobs: TvJobs,
    now: Long,
): TvPaneRow {
    val scanId = mediaServerScanJobId((jobs.state as? JobsUiState.Ready)?.jobs, server.mediaServer)
    val job = jobs.state.jobOrNull(scanId)
    val status = jobs.state.statusOf(scanId)
    val running = job?.running == true
    val line = if (status != TvJobStatus.Idle || running) jobLine(job, status, now) else null
    return TvPaneRow(
        key = KEY_MEDIA_SERVER,
        label = stringResource(server.mediaServer.labelRes()),
        body = stringResource(R.string.server_settings_media_server_caption),
        note = line,
        announceNote = line != null,
        options =
            listOf(
                if (running) {
                    TvPaneOption(
                        label = stringResource(R.string.tv_settings_cancel_library_scan),
                        icon = Icons.Filled.Cancel,
                        onSelect = { jobs.actions.onCancel(scanId) },
                    )
                } else {
                    TvPaneOption(
                        label = stringResource(R.string.tv_settings_start_library_scan),
                        icon = Icons.Filled.PlayArrow,
                        onSelect = { jobs.actions.onRun(scanId) },
                    )
                },
            ),
        icon = Icons.Filled.Storage,
    )
}

/**
 * Every scheduled job as a row, from the jobs view model's own list rather than the settings read. OK runs the job, or
 * stops it while it runs, and the row says what came of it in place, as a live region once pressed.
 */
@Composable
internal fun tvJobRows(
    jobs: TvJobs,
    ready: JobsUiState.Ready,
    now: Long,
): List<TvPaneRow> =
    ready.jobs.map { job ->
        val press = jobs.presses[job.id]
        key(job.id) {
            if (press?.holding == true) {
                LaunchedEffect(press) {
                    delay(JOB_MIN_WORKING_MILLIS)
                    jobs.presses.release(job.id)
                }
            }
        }
        TvPaneRow(
            key = "$KEY_JOB_PREFIX${job.id}",
            label = job.name,
            detail = jobLineText(jobLineOf(job.running, ready.statusOf(job.id), press), job, now),
            announceNote = press != null,
            options =
                listOf(
                    TvPaneOption(
                        label = job.name,
                        icon = Icons.Filled.Cached,
                        onSelect = {
                            jobs.presses.press(job.id, stopping = job.running)
                            if (job.running) jobs.actions.onCancel(job.id) else jobs.actions.onRun(job.id)
                        },
                    ),
                ),
            icon = Icons.Filled.Cached,
        )
    }

@Composable
private fun jobLineText(
    line: TvJobLine,
    job: ServerJob,
    now: Long,
): String =
    when (line) {
        TvJobLine.Failed -> stringResource(R.string.tv_settings_job_failed_short)
        TvJobLine.Working -> stringResource(R.string.tv_settings_job_working)
        TvJobLine.Stopped -> stringResource(R.string.tv_settings_job_stopped)
        TvJobLine.Done -> stringResource(R.string.tv_settings_job_done)
        TvJobLine.Running, TvJobLine.Schedule -> jobStatusText(job.running, job.nextRunMillis, now)
    }

internal const val KEY_JOB_PREFIX = "job:"
