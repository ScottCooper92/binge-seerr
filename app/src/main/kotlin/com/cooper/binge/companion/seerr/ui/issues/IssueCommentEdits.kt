package com.cooper.binge.companion.seerr.ui.issues

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrIssueCommentBody
import com.cooper.binge.companion.seerr.seerr.attempt
import com.cooper.binge.companion.seerr.seerr.isTransient
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.CrashBreadcrumbs
import com.cooper.binge.companion.seerr.ui.users.settings.SaveStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The comment edits of one issue page, saved as they are made (#930): the thread shows the new text at once, and the page
 * says Saving, then Saved. A write that fails keeps the text as an unsent edit, one per comment, which the page's Retry
 * sends again; each stays until that comment's edit lands. A refusal is not kept: the page re-reads the server and reports
 * the error. Leaving the page drops an unsent edit. A failed reload after a write that landed is still reported.
 * It writes to the page's [state], and reloads and reports through the page's own [reloadAfterWrite] and [report].
 */
internal class IssueCommentEdits(
    private val issueId: Int,
    private val connection: SeerrConnection,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val state: MutableStateFlow<IssueDetailUiState>,
    private val analytics: Analytics,
    private val crashBreadcrumbs: CrashBreadcrumbs,
    private val reloadAfterWrite: suspend () -> Throwable?,
    private val report: suspend (IssueDetailEvent) -> Unit,
) {
    fun edit(
        commentId: Int,
        message: String,
    ) {
        val ready = ready() ?: return
        val trimmed = message.trim()
        if (trimmed.isEmpty() || ready.commentAction != CommentAction.None || ready.action != IssueAction.None) return
        val edit = CommentEdit(commentId, trimmed)
        state.value =
            ready.copy(
                detail = ready.detail.withEdit(edit),
                commentAction = CommentAction.Editing(commentId),
                editStatus = SaveStatus.Saving,
            )
        crashBreadcrumbs.key("issue_id", issueId.toString())
        crashBreadcrumbs.key("comment_id", commentId.toString())
        crashBreadcrumbs.log("editing comment on issue")
        scope.launch(dispatcher) {
            attempt { connection.api().editIssueComment(commentId, SeerrIssueCommentBody(trimmed)) }
                .onSuccess {
                    // Landed, so it is no longer owed; an unsent edit to another comment still is.
                    state.updateReady { it.copy(unsentEdits = it.unsentEdits.filter { unsent -> unsent.commentId != commentId }) }
                    val reloadFailure = reloadAfterWrite()
                    if (reloadFailure == null) {
                        analytics.event(AnalyticsEvents.ISSUE_COMMENTED, mapOf(AnalyticsEvents.PARAM_ACTION to "edited"))
                    }
                    state.updateReady { it.copy(editStatus = SaveStatus.Saved) }
                    reloadFailure?.let { report(IssueDetailEvent.Failed(it.toSeerrError())) }
                }.onFailure { failure ->
                    val error = failure.toSeerrError()
                    if (error.isTransient) {
                        // One owed edit per comment: a second failure adds to the first rather than replacing it.
                        state.updateReady {
                            it.copy(
                                commentAction = CommentAction.None,
                                editStatus = SaveStatus.Idle,
                                unsentEdits = it.unsentEdits.filter { unsent -> unsent.commentId != commentId } + edit,
                            )
                        }
                    } else {
                        // A refusal would only be refused again: take the text off the page and say why, once.
                        state.updateReady {
                            it.copy(
                                commentAction = CommentAction.None,
                                editStatus = SaveStatus.Idle,
                                unsentEdits = it.unsentEdits.filter { unsent -> unsent.commentId != commentId },
                            )
                        }
                        reloadAfterWrite()
                        report(IssueDetailEvent.Failed(error))
                    }
                }
        }
    }

    /** Sends the first unsent edit, read when this is called so a Retry never sends text that has since changed. */
    fun retry() {
        val edit = ready()?.unsentEdits?.firstOrNull() ?: return
        edit(edit.commentId, edit.message)
    }

    private fun ready(): IssueDetailUiState.Ready? = state.value as? IssueDetailUiState.Ready
}
