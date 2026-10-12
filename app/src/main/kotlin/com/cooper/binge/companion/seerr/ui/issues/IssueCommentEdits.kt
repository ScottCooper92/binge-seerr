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
    /**
     * Each comment's text as the server last confirmed it, kept from when its first unsent edit began. Once an edit is owed,
     * the page shows that edit, so this is the only place a refused retry can put the server's text back from.
     */
    private val confirmed = mutableMapOf<Int, String>()

    fun edit(
        commentId: Int,
        message: String,
    ) {
        val ready = ready() ?: return
        val trimmed = message.trim()
        if (trimmed.isEmpty() || ready.commentAction != CommentAction.None || ready.action != IssueAction.None) return
        val edit = CommentEdit(commentId, trimmed)
        if (ready.unsentEdits.none { it.commentId == commentId }) {
            ready.detail.messageOf(commentId)?.let { confirmed[commentId] = it }
        }
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
                    confirmed.remove(commentId)
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
                        // A refusal would only be refused again: take the text off the page and say why, once. The
                        // comment shows an earlier owed edit with other text if there is one, else the server's text, and
                        // goes back before the reload, so a reload that fails cannot leave refused or unowed text shown.
                        state.updateReady {
                            val owed = it.unsentEdits.filter { unsent -> unsent != edit }
                            val shown =
                                owed.firstOrNull { unsent -> unsent.commentId == commentId }
                                    ?: confirmed[commentId]?.let { text -> CommentEdit(commentId, text) }
                            if (owed.none { unsent -> unsent.commentId == commentId }) confirmed.remove(commentId)
                            it.copy(
                                detail = shown?.let(it.detail::withEdit) ?: it.detail,
                                commentAction = CommentAction.None,
                                editStatus = SaveStatus.Idle,
                                unsentEdits = owed,
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
