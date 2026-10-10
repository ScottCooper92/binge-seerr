package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueCommentBody
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The optimistic comment outbox of one issue page: a posted draft shows at once as a pending row,
 * the server's confirmed copy takes its place when the post lands, and a failed one stays with a
 * retry, an edit or a drop. It writes to the page's [state] and owns nothing else of it.
 */
internal class IssueCommentOutbox(
    private val issueId: Int,
    private val connection: SeerrConnection,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val state: MutableStateFlow<IssueDetailUiState>,
    private val analytics: Analytics,
    private val crashBreadcrumbs: CrashBreadcrumbs,
) {
    private var nextLocalId = 1L

    /** The draft goes into the outbox and the send runs behind it; the composer clears at once. */
    fun post() {
        val ready = ready() ?: return
        val message = ready.draft.trim()
        if (message.isEmpty() || !ready.detail.canComment) return
        val entry =
            OutboxComment(
                localId = nextLocalId++,
                message = message,
                author = ready.detail.currentUserName,
                submittedAtMillis = System.currentTimeMillis(),
                state = SendState.Sending,
            )
        state.value = ready.copy(draft = "", outbox = ready.outbox + entry)
        crashBreadcrumbs.key("issue_id", issueId.toString())
        crashBreadcrumbs.log("posting comment on issue")
        scope.launch(dispatcher) { send(entry.localId, message) }
    }

    fun retry(localId: Long) {
        val entry = entry(localId) ?: return
        update(localId) { it.copy(state = SendState.Sending) }
        scope.launch(dispatcher) { send(localId, entry.message) }
    }

    /**
     * Edits a pending comment and sends the new text at once. A comment whose send is in flight is left alone: cancelling
     * the coroutine stops this app waiting but cannot recall a request whose body already reached the server, so a second
     * send could land beside the first. The sheet turns Edit off while it is in flight; it comes back once the send lands
     * or fails.
     */
    fun edit(
        localId: Long,
        message: String,
    ) {
        val trimmed = message.trim()
        val entry = entry(localId)
        if (trimmed.isEmpty() || entry == null || entry.state == SendState.Sending) return
        update(localId) { it.copy(message = trimmed, state = SendState.Sending) }
        scope.launch(dispatcher) { send(localId, trimmed) }
    }

    /**
     * Discards a failed pending comment. One whose send is in flight is left alone, for the reason [edit] gives: a request
     * already on the wire may still land, and it would then show up on the thread after being discarded. The sheet turns
     * Discard off while it is in flight.
     */
    fun drop(localId: Long) {
        if (entry(localId)?.state == SendState.Sending) return
        state.updateReady { it.copy(outbox = it.outbox.filterNot { entry -> entry.localId == localId }) }
    }

    /**
     * The confirmed comment is resolved by diffing the post's answer against what this issue's
     * comments look like right when this confirmation is applied, not a snapshot taken before the
     * request went out: a sibling outbox entry that lands first is folded into `detail.comments`
     * before this one resolves, so it's already "known" and can't be reclaimed here even when both
     * entries sent identical text. The newest id alone can't be trusted either, because another
     * comment on the same issue - another user's, another device's, or a second outbox entry - can
     * arrive between this request and its response and outrank it.
     */
    private suspend fun send(
        localId: Long,
        message: String,
    ) {
        attempt {
            val issue = connection.api().commentOnIssue(issueId, SeerrIssueCommentBody(message))
            val user = attempt { connection.authenticatedUser() }.getOrNull()
            issue to user
        }.onSuccess { (issue, user) ->
            var matched = false
            state.updateReady { ready ->
                val knownIds = setOfNotNull(ready.detail.report?.id) + ready.detail.comments.map { it.id }
                val candidates = issue.comments.filter { it.id !in knownIds && it.message == message }
                val confirmedDto =
                    user?.let { u -> candidates.firstOrNull { it.user?.id == u.id } } ?: candidates.minByOrNull { it.id }
                val confirmed = confirmedDto?.toIssueComment(user?.id)
                if (confirmed == null) {
                    ready
                } else {
                    matched = true
                    ready.copy(
                        detail = ready.detail.copy(comments = ready.detail.comments + confirmed.copy(isMine = true)),
                        outbox = ready.outbox.filterNot { it.localId == localId },
                    )
                }
            }
            if (matched) analytics.event(AnalyticsEvents.ISSUE_COMMENTED, mapOf(AnalyticsEvents.PARAM_ACTION to "posted"))
            if (!matched) update(localId) { it.copy(state = SendState.Failed(retryable = true)) }
        }.onFailure { failure ->
            // A cancellation is the page going away, not a failed send: there is nothing left to show it on.
            if (failure is CancellationException) throw failure
            val retryable = failure.toSeerrError().let { it != SeerrError.Forbidden && it != SeerrError.Unauthorized }
            update(localId) { it.copy(state = SendState.Failed(retryable)) }
        }
    }

    private fun ready(): IssueDetailUiState.Ready? = state.value as? IssueDetailUiState.Ready

    private fun entry(localId: Long): OutboxComment? = ready()?.outbox?.firstOrNull { it.localId == localId }

    private fun update(
        localId: Long,
        transform: (OutboxComment) -> OutboxComment,
    ) = state.updateReady { ready -> ready.copy(outbox = ready.outbox.map { if (it.localId == localId) transform(it) else it }) }
}

/** Applies [transform] to the page if it is [IssueDetailUiState.Ready], and leaves any other state alone. */
internal fun MutableStateFlow<IssueDetailUiState>.updateReady(transform: (IssueDetailUiState.Ready) -> IssueDetailUiState.Ready) =
    update { current -> (current as? IssueDetailUiState.Ready)?.let(transform) ?: current }
