package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.labelRes
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.MEDIA_SERVER_SCAN_JOB_ID
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
        TvJobStatus.Failed -> stringResource(R.string.tv_settings_job_failed_note)
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
    val job = jobs.state.jobOrNull(MEDIA_SERVER_SCAN_JOB_ID)
    val status = jobs.state.statusOf(MEDIA_SERVER_SCAN_JOB_ID)
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
                                    onSelect = { jobs.actions.onCancel(MEDIA_SERVER_SCAN_JOB_ID) },
                                )
                            } else {
                                TvPaneOption(
                                    label = stringResource(R.string.tv_settings_start_library_scan),
                                    icon = Icons.Filled.PlayArrow,
                                    onSelect = { jobs.actions.onRun(MEDIA_SERVER_SCAN_JOB_ID) },
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
                TvPaneRow(
                    key = "$KEY_JOB_PREFIX${job.id}",
                    label = job.name,
                    detail = jobLine(job, ready.statusOf(job.id), now),
                    options = listOf(TvPaneOption(label = job.name, icon = Icons.Filled.Cached, onSelect = { jobs.onOpenJob(job.id) })),
                    icon = Icons.Filled.Cached,
                )
            },
    )

internal const val KEY_JOB_PREFIX = "job:"
