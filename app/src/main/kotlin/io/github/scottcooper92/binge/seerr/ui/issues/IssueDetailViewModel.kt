package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.IssueStore
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueCommentBody
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One issue as a page: the report, the thread, and the composer. Posting is optimistic: the
 * comment shows at once as a pending row, the server's confirmed copy takes its place when the
 * post lands, and a failed one stays in the outbox with a retry, an edit or a drop. Editing or
 * deleting a comment on the server reloads the page.
 */
@HiltViewModel(assistedFactory = IssueDetailViewModel.Factory::class)
class IssueDetailViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
        private val store: IssueStore,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val issueId: Int,
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
        private val savedState: SavedStateHandle = SavedStateHandle(),
    ) : ViewModel() {
        private val state = MutableStateFlow<IssueDetailUiState>(IssueDetailUiState.Loading)
        val uiState: StateFlow<IssueDetailUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<IssueDetailEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<IssueDetailEvent> = eventFlow.asSharedFlow()

        private val outbox =
            IssueCommentOutbox(issueId, connection, viewModelScope, dispatcher, state, analytics, crashBreadcrumbs)

        init {
            reload()
        }

        fun reload() {
            state.update { current ->
                when (current) {
                    is IssueDetailUiState.Ready -> current
                    is IssueDetailUiState.Seeded -> current.copy(error = null)
                    else -> IssueDetailUiState.Loading
                }
            }
            viewModelScope.launch(dispatcher) {
                seedFromCache()
                attempt { load() }
                    .onSuccess { detail ->
                        state.update { current ->
                            val ready = current as? IssueDetailUiState.Ready
                            IssueDetailUiState.Ready(
                                detail = detail,
                                // A comment half typed when the process was killed comes back in the composer (#1026).
                                draft = ready?.draft ?: savedState.get<String>(COMMENT_DRAFT_KEY).orEmpty(),
                                outbox = ready?.outbox.orEmpty(),
                            )
                        }
                    }.onFailure { failure ->
                        // A page already showing keeps its stale issue rather than blanking to the error.
                        state.update { current ->
                            when (current) {
                                is IssueDetailUiState.Ready -> current
                                is IssueDetailUiState.Seeded -> current.copy(error = failure.toSeerrError())
                                else -> IssueDetailUiState.Error(failure.toSeerrError())
                            }
                        }
                    }
            }
        }

        /** Shows the cached row's header in place of the skeleton, if a list has the issue and nothing has landed yet. */
        private suspend fun seedFromCache() {
            if (state.value !is IssueDetailUiState.Loading) return
            val item = attempt { store.byId(issueId)?.toIssueItem() }.getOrNull() ?: return
            state.update { current -> if (current is IssueDetailUiState.Loading) IssueDetailUiState.Seeded(item) else current }
        }

        fun setDraft(text: String) {
            updateReady { it.copy(draft = text) }
            savedState[COMMENT_DRAFT_KEY] = text
        }

        /** The draft goes into the outbox and the send runs behind it; the composer clears at once. */
        fun postComment() {
            outbox.post()
            if ((state.value as? IssueDetailUiState.Ready)?.draft.isNullOrEmpty()) savedState.remove<String>(COMMENT_DRAFT_KEY)
        }

        fun retryOutbox(localId: Long) = outbox.retry(localId)

        fun editOutbox(
            localId: Long,
            message: String,
        ) = outbox.edit(localId, message)

        fun dropOutbox(localId: Long) = outbox.drop(localId)

        fun editComment(
            commentId: Int,
            message: String,
        ) {
            val ready = ready() ?: return
            val trimmed = message.trim()
            if (trimmed.isEmpty() || ready.commentAction != CommentAction.None || ready.action != IssueAction.None) return
            state.value = ready.copy(commentAction = CommentAction.Editing(commentId))
            crashBreadcrumbs.key("issue_id", issueId.toString())
            crashBreadcrumbs.key("comment_id", commentId.toString())
            crashBreadcrumbs.log("editing comment on issue")
            viewModelScope.launch(dispatcher) {
                attempt { connection.api().editIssueComment(commentId, SeerrIssueCommentBody(trimmed)) }
                    .onSuccess {
                        val reloadFailure = reloadAfterWrite()
                        if (reloadFailure == null) {
                            analytics.event(AnalyticsEvents.ISSUE_COMMENTED, mapOf(AnalyticsEvents.PARAM_ACTION to "edited"))
                        }
                        eventFlow.emit(
                            reloadFailure?.let { IssueDetailEvent.Failed(it.toSeerrError()) } ?: IssueDetailEvent.CommentEdited,
                        )
                    }.onFailure { failure ->
                        updateReady { it.copy(commentAction = CommentAction.None) }
                        eventFlow.emit(IssueDetailEvent.Failed(failure.toSeerrError()))
                    }
            }
        }

        /** Resolves an open issue or reopens a resolved one; the cached row moves with it, so the browser agrees at once. */
        fun toggleStatus() {
            val ready = ready() ?: return
            if (ready.action != IssueAction.None || ready.commentAction != CommentAction.None || !ready.detail.canResolve) return
            val resolving = ready.detail.item.status == IssueStatus.Open
            state.value = ready.copy(action = IssueAction.UpdatingStatus)
            crashBreadcrumbs.key("issue_id", issueId.toString())
            crashBreadcrumbs.log(if (resolving) "resolving issue" else "reopening issue")
            viewModelScope.launch(dispatcher) {
                attempt {
                    connection.api().setIssueStatus(issueId, if (resolving) STATUS_RESOLVED else STATUS_OPEN)
                    store.updateStatus(issueId, (if (resolving) IssueStatus.Resolved else IssueStatus.Open).name)
                }.onSuccess {
                    val reloadFailure = reloadAfterWrite()
                    if (reloadFailure == null) {
                        val action = if (resolving) "resolved" else "reopened"
                        analytics.event(AnalyticsEvents.ISSUE_MODERATED, mapOf(AnalyticsEvents.PARAM_ACTION to action))
                    }
                    eventFlow.emit(
                        reloadFailure?.let { IssueDetailEvent.Failed(it.toSeerrError()) }
                            ?: (if (resolving) IssueDetailEvent.IssueResolved else IssueDetailEvent.IssueReopened),
                    )
                }.onFailure { failure ->
                    updateReady { it.copy(action = IssueAction.None) }
                    eventFlow.emit(IssueDetailEvent.Failed(failure.toSeerrError()))
                }
            }
        }

        /** Removes the report and its whole thread; the page has nothing left to show, so it pops. */
        fun deleteIssue() {
            val ready = ready() ?: return
            if (ready.action != IssueAction.None || ready.commentAction != CommentAction.None || !ready.detail.canDelete) return
            state.value = ready.copy(action = IssueAction.Deleting)
            crashBreadcrumbs.key("issue_id", issueId.toString())
            crashBreadcrumbs.log("deleting issue")
            viewModelScope.launch(dispatcher) {
                attempt {
                    connection.api().deleteIssue(issueId)
                    store.delete(issueId)
                }.onSuccess {
                    analytics.event(AnalyticsEvents.ISSUE_MODERATED, mapOf(AnalyticsEvents.PARAM_ACTION to "deleted"))
                    eventFlow.emit(IssueDetailEvent.IssueDeleted)
                }.onFailure { failure ->
                    updateReady { it.copy(action = IssueAction.None) }
                    eventFlow.emit(IssueDetailEvent.Failed(failure.toSeerrError()))
                }
            }
        }

        fun deleteComment(commentId: Int) {
            val ready = ready() ?: return
            if (ready.commentAction != CommentAction.None || ready.action != IssueAction.None) return
            state.value = ready.copy(commentAction = CommentAction.Deleting(commentId))
            crashBreadcrumbs.key("issue_id", issueId.toString())
            crashBreadcrumbs.key("comment_id", commentId.toString())
            crashBreadcrumbs.log("deleting comment on issue")
            viewModelScope.launch(dispatcher) {
                attempt { connection.api().deleteIssueComment(commentId) }
                    .onSuccess {
                        val reloadFailure = reloadAfterWrite()
                        if (reloadFailure == null) {
                            analytics.event(AnalyticsEvents.ISSUE_COMMENTED, mapOf(AnalyticsEvents.PARAM_ACTION to "deleted"))
                        }
                        eventFlow.emit(
                            reloadFailure?.let { IssueDetailEvent.Failed(it.toSeerrError()) } ?: IssueDetailEvent.CommentDeleted,
                        )
                    }.onFailure { failure ->
                        updateReady { it.copy(commentAction = CommentAction.None) }
                        eventFlow.emit(IssueDetailEvent.Failed(failure.toSeerrError()))
                    }
            }
        }

        /**
         * Reloads after a write that already landed on the server, and reports whether the reload
         * itself failed, so a caller never announces success on a page still showing the stale comment.
         */
        private suspend fun reloadAfterWrite(): Throwable? {
            val result = attempt { load() }
            updateReady { ready ->
                result
                    .getOrNull()
                    ?.let { detail -> ready.copy(detail = detail, commentAction = CommentAction.None, action = IssueAction.None) }
                    ?: ready.copy(commentAction = CommentAction.None, action = IssueAction.None)
            }
            return result.exceptionOrNull()
        }

        private suspend fun load(): IssueDetail =
            coroutineScope {
                val api = connection.api()
                val user = async { attempt { connection.authenticatedUser() }.getOrNull() }
                val dto = api.issue(issueId)
                val item = checkNotNull(dto.toIssueItem(api, titles::get)) { "Unrenderable media type" }
                dto.toDetail(item, user.await(), connection.current().baseUrl, connection.profile())
            }

        private fun ready(): IssueDetailUiState.Ready? = state.value as? IssueDetailUiState.Ready

        private fun updateReady(transform: (IssueDetailUiState.Ready) -> IssueDetailUiState.Ready) = state.updateReady(transform)

        @AssistedFactory
        interface Factory {
            fun create(issueId: Int): IssueDetailViewModel
        }

        private companion object {
            const val COMMENT_DRAFT_KEY = "issue.comment.draft"
        }
    }
