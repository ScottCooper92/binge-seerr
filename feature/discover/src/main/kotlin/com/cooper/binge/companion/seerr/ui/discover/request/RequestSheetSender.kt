package com.cooper.binge.companion.seerr.ui.discover.request

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import com.cooper.binge.companion.seerr.discover.request.RequestSubmission
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.serverMessage
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FOOTER_FAILURE_MILLIS = 4_000L

/**
 * Sends the sheet's request and tells the state how it went. [onDone] is called with any answer that leaves nothing more to do;
 * the sheet then reads itself again, because its view model outlives the sheet and the next opening must not start from the old send.
 */
class RequestSheetSender(
    private val repository: RequestSheetRepository,
    private val target: RequestSheetTarget,
    private val store: RequestSheetStore,
    private val scope: CoroutineScope,
    private val onDone: (RequestResult) -> Unit,
) {
    /** The timer that turns the red footer back; a new send or a new failure replaces it, so an old one cannot cut a newer window short. */
    private var footerTimer: Job? = null

    fun submit() {
        val current = store.ready() ?: return
        if (!current.canSubmit) return
        footerTimer?.cancel()
        store.set(current.copy(submitting = true, problem = null, failure = null, footerFailed = false, failureDialog = false))
        val films = current.films
        if (films != null) submitCollection(current, films.requestIds) else submitTitle(current)
    }

    /** A collection goes as one request per ticked film; the answer says how many the server made and the first refusal. */
    private fun submitCollection(
        current: RequestSheetUiState.Ready,
        filmIds: List<Int>,
    ) {
        val submissions =
            filmIds.map { id ->
                RequestSubmission(
                    kind = TitleKind.Movie,
                    tmdbId = id,
                    seasons = emptyList(),
                    is4k = current.use4k,
                    destination = current.destination?.toDestination(),
                    userId = current.requestFor?.id,
                )
            }
        scope.launch {
            val answer = repository.submitAll(submissions)
            val failure = answer.failure
            when {
                failure != null && answer.created == 0 -> onSendFailed(failure.toProblem())
                failure != null -> onDone(RequestResult.PartiallyCreated)
                answer.created > 0 -> onDone(RequestResult.Created)
                else -> onDone(RequestResult.AlreadyRequested)
            }
        }
    }

    private fun submitTitle(current: RequestSheetUiState.Ready) {
        val submission =
            RequestSubmission(
                kind = target.kind,
                tmdbId = target.tmdbId,
                seasons = current.seasons?.requestSeasons ?: target.seasons,
                is4k = current.use4k,
                destination = current.destination?.toDestination(),
                userId = current.requestFor?.id,
            )
        scope.launch {
            attempt { repository.submit(submission) }.fold(
                onSuccess = onDone,
                onFailure = { error -> onSendFailed(error.toProblem()) },
            )
        }
    }

    private fun onSendFailed(problem: RequestProblem) {
        val answered =
            problem.cause == RequestProblemCause.Quota ||
                problem.cause == RequestProblemCause.NotAllowed ||
                (problem.cause == RequestProblemCause.Other && !problem.detail.isNullOrBlank())
        if (answered) {
            store.update { it.copy(submitting = false, problem = problem) }
            return
        }
        store.update { it.copy(submitting = false, failure = problem, footerFailed = true) }
        footerTimer?.cancel()
        footerTimer =
            scope.launch {
                delay(FOOTER_FAILURE_MILLIS)
                store.update { if (!it.failureDialog) it.copy(footerFailed = false) else it }
            }
    }
}

private fun Throwable.toProblem(): RequestProblem {
    val cause =
        when (toSeerrError()) {
            SeerrError.Quota -> RequestProblemCause.Quota
            SeerrError.Forbidden, SeerrError.Unauthorized -> RequestProblemCause.NotAllowed
            SeerrError.Unreachable, SeerrError.LocalNetworkDenied, SeerrError.NotConnected -> RequestProblemCause.Unreachable
            else -> RequestProblemCause.Other
        }
    return RequestProblem(cause, serverMessage())
}
