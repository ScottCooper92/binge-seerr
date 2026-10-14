package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.labelRes
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.ServerJob

/** What a television can do to a scheduled job: start it, or stop it while it runs. */
internal class TvJobActions(
    val onRun: (id: String) -> Unit,
    val onCancel: (id: String) -> Unit,
) {
    companion object {
        val None = TvJobActions(onRun = {}, onCancel = {})
    }
}

/** What the sheet shows of the jobs: the list's state, what can be done to a job, and how to open one's page. */
internal class TvJobs(
    val state: JobsUiState,
    val actions: TvJobActions,
    val onOpenJob: (id: String) -> Unit,
)

/** Where a job's last action stands, as its row and page show it. */
internal enum class TvJobStatus { Idle, Busy, Failed }

/** The status of [id] in [this]: busy while a call is in flight, failed once one was refused, else idle. */
internal fun JobsUiState.statusOf(id: String): TvJobStatus =
    when (this) {
        is JobsUiState.Ready ->
            when (id) {
                in busyIds -> TvJobStatus.Busy
                in failedIds -> TvJobStatus.Failed
                else -> TvJobStatus.Idle
            }
        JobsUiState.Loading -> TvJobStatus.Busy
        is JobsUiState.Error -> TvJobStatus.Failed
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
        TvJobStatus.Failed -> stringResource(R.string.tv_settings_job_failed_short_proto)
        TvJobStatus.Idle -> job?.let { jobStatusText(it.running, it.nextRunMillis, now) }
    }

/**
 * The media server row: start the library scan, the "it downloaded but isn't showing" fix from the sofa, or cancel it
 * while it runs. Its line is the scan's own state in place, else the server it scans.
 */
@Composable
internal fun mediaServerGroup(
    server: ServerSummary,
    jobs: TvJobs,
    now: Long,
): TvPaneGroup {
    // PROTOTYPE fix: Jellyfin and Emby servers run the full scan as `jellyfin-full-scan`, Plex as `plex-full-scan`
    // (server/job/schedule.ts upstream), so the id is whichever the server's own job list carries.
    val listed = (jobs.state as? JobsUiState.Ready)?.jobs.orEmpty().map { it.id }
    val scanId = if (JELLYFIN_FULL_SCAN in listed) JELLYFIN_FULL_SCAN else PLEX_FULL_SCAN
    val job = jobs.state.jobOrNull(scanId)
    val status = jobs.state.statusOf(scanId)
    val running = job?.running == true
    val line = if (status != TvJobStatus.Idle || running) jobLine(job, status, now) else null
    return TvPaneGroup(
        title = stringResource(R.string.server_settings_media_server),
        rows =
            listOf(
                TvPaneRow(
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
                ),
            ),
    )
}

/** Every scheduled job as a row that opens its page, from the jobs view model's own list rather than the settings read. */
@Composable
internal fun tvJobsGroup(
    jobs: TvJobs,
    ready: JobsUiState.Ready,
    now: Long,
): TvPaneGroup =
    TvPaneGroup(
        title = stringResource(R.string.settings_group_system),
        rows =
            ready.jobs.map { job ->
                val status = ready.statusOf(job.id)
                val hold = key(job.id) { rememberJobHold() }
                val line =
                    when {
                        hold.holding || status == TvJobStatus.Busy -> stringResource(R.string.tv_settings_job_working)
                        status == TvJobStatus.Failed -> stringResource(R.string.tv_settings_job_failed_short_proto)
                        hold.stopped && !job.running -> stringResource(R.string.tv_settings_job_stopped_proto)
                        hold.ran && !job.running -> stringResource(R.string.tv_settings_job_done_proto)
                        else -> jobLine(job, status, now)
                    }
                TvPaneRow(
                    key = "$KEY_JOB_PREFIX${job.id}",
                    label = job.name,
                    detail = line,
                    announceNote = hold.pressed != 0L,
                    // PROTOTYPE: OK runs the job, or stops it while it runs; its page is for a long press, later.
                    options =
                        listOf(
                            TvPaneOption(
                                label = job.name,
                                icon = Icons.Filled.Cached,
                                onSelect = {
                                    hold.press(stopping = job.running)
                                    if (job.running) jobs.actions.onCancel(job.id) else jobs.actions.onRun(job.id)
                                },
                            ),
                        ),
                    icon = Icons.Filled.Cached,
                )
            },
    )

internal const val KEY_JOB_PREFIX = "job:"

/** PROTOTYPE: the shortest a press reads as Working, so a job the server finishes in 20ms still shows that it ran. */
private const val JOB_MIN_WORKING_MILLIS = 900L

/** A row's own memory of being pressed: Working for at least [JOB_MIN_WORKING_MILLIS], then which way it went. */
private class JobHold {
    var pressed by mutableLongStateOf(0L)
    var holding by mutableStateOf(false)
    var stopped by mutableStateOf(false)
    val ran get() = pressed != 0L && !stopped

    fun press(stopping: Boolean) {
        stopped = stopping
        pressed = android.os.SystemClock.uptimeMillis()
    }
}

@Composable
private fun rememberJobHold(): JobHold {
    val hold = remember { JobHold() }
    LaunchedEffect(hold.pressed) {
        if (hold.pressed == 0L) return@LaunchedEffect
        hold.holding = true
        delay(JOB_MIN_WORKING_MILLIS)
        hold.holding = false
    }
    return hold
}
private const val PLEX_FULL_SCAN = "plex-full-scan"
private const val JELLYFIN_FULL_SCAN = "jellyfin-full-scan"
