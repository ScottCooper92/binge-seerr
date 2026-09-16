package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrJobDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrJobScheduleBody
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RUNNING_REFRESH_MILLIS = 5_000L

/**
 * The jobs page: every scheduled job, run now, cancelled, or given a new schedule. While any job
 * is running the list is re-read on a short interval, so the running state clears on its own.
 */
@HiltViewModel
class JobsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        private val state = MutableStateFlow<JobsUiState>(JobsUiState.Loading)
        val uiState: StateFlow<JobsUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

        internal var runningRefreshMillis = RUNNING_REFRESH_MILLIS
        private var refresh: Job? = null

        init {
            reload()
        }

        fun reload() {
            state.value = JobsUiState.Loading
            viewModelScope.launch {
                runCatching { connection.api().jobs().map { it.toServerJob() } }
                    .onSuccess { jobs -> setJobs(jobs) }
                    .onFailure { state.value = JobsUiState.Error(it.toSeerrError()) }
            }
        }

        fun run(id: String) = act(id) { api -> api.runJob(id) }

        /**
         * Same call as [run], but with a notice on success — for a caller with no jobs list of its own
         * to read the outcome off (the TV settings board's one confirmed option), which needs telling
         * rather than a row it does not render.
         */
        fun run(
            id: String,
            noticeRes: Int,
        ) = act(id, noticeRes) { api -> api.runJob(id) }

        fun cancel(id: String) = act(id) { api -> api.cancelJob(id) }

        fun schedule(
            id: String,
            cron: String,
        ) = act(id, R.string.server_settings_job_scheduled) { api -> api.scheduleJob(id, SeerrJobScheduleBody(cron.trim())) }

        private fun act(
            id: String,
            noticeRes: Int? = null,
            call: suspend (SeerrApi) -> SeerrJobDto,
        ) {
            val ready = state.value as? JobsUiState.Ready ?: return
            if (id in ready.busyIds) return
            state.value = ready.copy(busyIds = ready.busyIds + id)
            viewModelScope.launch {
                val outcome = runCatching { call(connection.api()).toServerJob() }
                outcome.onSuccess { updated -> setJobs(jobs().map { if (it.id == updated.id) updated else it }) }
                // Before the event, not after it: a terminal event is this action's last observable
                // effect, so anything reacting to one sees the job already released rather than a
                // state that still says busy for however long the emitting coroutine takes to resume.
                state.update { current -> (current as? JobsUiState.Ready)?.copy(busyIds = current.busyIds - id) ?: current }
                outcome
                    .onSuccess { noticeRes?.let { eventFlow.emit(EditorEvent.Notice(it)) } }
                    .onFailure { failure -> eventFlow.emit(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        private fun jobs(): List<ServerJob> = (state.value as? JobsUiState.Ready)?.jobs.orEmpty()

        private fun setJobs(jobs: List<ServerJob>) {
            state.update { current -> JobsUiState.Ready(jobs, busyIds = (current as? JobsUiState.Ready)?.busyIds.orEmpty()) }
            if (jobs.any { it.running }) followRunning() else refresh?.cancel()
        }

        private fun followRunning() {
            if (refresh?.isActive == true) return
            refresh =
                viewModelScope.launch {
                    while (jobs().any { it.running }) {
                        delay(runningRefreshMillis)
                        runCatching { connection.api().jobs().map { it.toServerJob() } }.onSuccess { jobs ->
                            state.update { current -> (current as? JobsUiState.Ready)?.copy(jobs = jobs) ?: current }
                        }
                    }
                }
        }
    }
