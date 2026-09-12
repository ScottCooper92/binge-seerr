package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.IssueStore
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueCommentBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
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
        @Assisted private val issueId: Int,
    ) : ViewModel() {
        private val state = MutableStateFlow<IssueDetailUiState>(IssueDetailUiState.Loading)
        val uiState: StateFlow<IssueDetailUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<IssueDetailEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<IssueDetailEvent> = eventFlow.asSharedFlow()

        private var nextLocalId = 1L

        init {
            reload()
        }

        fun reload() {
            if (state.value !is IssueDetailUiState.Ready) state.value = IssueDetailUiState.Loading
            viewModelScope.launch {
                runCatching { load() }
                    .onSuccess { detail ->
                        state.update { current ->
                            val ready = current as? IssueDetailUiState.Ready
                            IssueDetailUiState.Ready(
                                detail = detail,
                                draft = ready?.draft.orEmpty(),
                                outbox = ready?.outbox.orEmpty(),
                            )
                        }
                    }.onFailure { failure ->
                        // A page already showing keeps its stale issue rather than blanking to the error.
                        state.update { current ->
                            (current as? IssueDetailUiState.Ready) ?: IssueDetailUiState.Error(failure.toSeerrError())
                        }
                    }
            }
        }

        fun setDraft(text: String) = updateReady { it.copy(draft = text) }

        /** The draft goes into the outbox and the send runs behind it; the composer clears at once. */
        fun postComment() {
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
            viewModelScope.launch { send(entry.localId, message) }
        }

        fun retryOutbox(localId: Long) {
            val entry = outbox(localId) ?: return
            updateOutbox(localId) { it.copy(state = SendState.Sending) }
            viewModelScope.launch { send(localId, entry.message) }
        }

        /** A pending comment never reached the server, so its edit is local and re-sent at once. */
        fun editOutbox(
            localId: Long,
            message: String,
        ) {
            val trimmed = message.trim()
            if (trimmed.isEmpty() || outbox(localId) == null) return
            updateOutbox(localId) { it.copy(message = trimmed, state = SendState.Sending) }
            viewModelScope.launch { send(localId, trimmed) }
        }

        fun dropOutbox(localId: Long) = updateReady { it.copy(outbox = it.outbox.filterNot { entry -> entry.localId == localId }) }

        fun editComment(
            commentId: Int,
            message: String,
        ) {
            val ready = ready() ?: return
            val trimmed = message.trim()
            if (trimmed.isEmpty() || ready.commentAction != CommentAction.None) return
            state.value = ready.copy(commentAction = CommentAction.Editing(commentId))
            viewModelScope.launch {
                runCatching { connection.api().editIssueComment(commentId, SeerrIssueCommentBody(trimmed)) }
                    .onSuccess {
                        reloadAfterWrite()
                        eventFlow.emit(IssueDetailEvent.CommentEdited)
                    }.onFailure { failure ->
                        updateReady { it.copy(commentAction = CommentAction.None) }
                        eventFlow.emit(IssueDetailEvent.Failed(failure.toSeerrError()))
                    }
            }
        }

        /** Resolves an open issue or reopens a resolved one; the cached row moves with it, so the browser agrees at once. */
        fun toggleStatus() {
            val ready = ready() ?: return
            if (ready.action != IssueAction.None || !ready.detail.canResolve) return
            val resolving = ready.detail.item.status == IssueStatus.Open
            state.value = ready.copy(action = IssueAction.UpdatingStatus)
            viewModelScope.launch {
                runCatching {
                    connection.api().setIssueStatus(issueId, if (resolving) STATUS_RESOLVED else STATUS_OPEN)
                    store.updateStatus(issueId, (if (resolving) IssueStatus.Resolved else IssueStatus.Open).name)
                }.onSuccess {
                    reloadAfterWrite()
                    eventFlow.emit(if (resolving) IssueDetailEvent.IssueResolved else IssueDetailEvent.IssueReopened)
                }.onFailure { failure ->
                    updateReady { it.copy(action = IssueAction.None) }
                    eventFlow.emit(IssueDetailEvent.Failed(failure.toSeerrError()))
                }
            }
        }

        /** Removes the report and its whole thread; the page has nothing left to show, so it pops. */
        fun deleteIssue() {
            val ready = ready() ?: return
            if (ready.action != IssueAction.None || !ready.detail.canDelete) return
            state.value = ready.copy(action = IssueAction.Deleting)
            viewModelScope.launch {
                runCatching {
                    connection.api().deleteIssue(issueId)
                    store.delete(issueId)
                }.onSuccess { eventFlow.emit(IssueDetailEvent.IssueDeleted) }
                    .onFailure { failure ->
                        updateReady { it.copy(action = IssueAction.None) }
                        eventFlow.emit(IssueDetailEvent.Failed(failure.toSeerrError()))
                    }
            }
        }

        fun deleteComment(commentId: Int) {
            val ready = ready() ?: return
            if (ready.commentAction != CommentAction.None) return
            state.value = ready.copy(commentAction = CommentAction.Deleting(commentId))
            viewModelScope.launch {
                runCatching { connection.api().deleteIssueComment(commentId) }
                    .onSuccess {
                        reloadAfterWrite()
                        eventFlow.emit(IssueDetailEvent.CommentDeleted)
                    }.onFailure { failure ->
                        updateReady { it.copy(commentAction = CommentAction.None) }
                        eventFlow.emit(IssueDetailEvent.Failed(failure.toSeerrError()))
                    }
            }
        }

        /** The confirmed comment is the newest on the issue the post answers with; it replaces the pending row. */
        private suspend fun send(
            localId: Long,
            message: String,
        ) {
            runCatching {
                val issue = connection.api().commentOnIssue(issueId, SeerrIssueCommentBody(message))
                val user = runCatching { connection.authenticatedUser() }.getOrNull()
                issue.comments.maxByOrNull { it.id }?.toIssueComment(user?.id) ?: error("No comment on the answer")
            }.onSuccess { confirmed ->
                updateReady { ready ->
                    ready.copy(
                        detail = ready.detail.copy(comments = ready.detail.comments + confirmed.copy(isMine = true)),
                        outbox = ready.outbox.filterNot { it.localId == localId },
                    )
                }
            }.onFailure { failure ->
                val retryable = failure.toSeerrError().let { it != SeerrError.Forbidden && it != SeerrError.Unauthorized }
                updateOutbox(localId) { it.copy(state = SendState.Failed(retryable)) }
            }
        }

        private suspend fun reloadAfterWrite() {
            val detail = runCatching { load() }.getOrNull()
            updateReady { ready ->
                ready.copy(detail = detail ?: ready.detail, commentAction = CommentAction.None, action = IssueAction.None)
            }
        }

        private suspend fun load(): IssueDetail =
            coroutineScope {
                val api = connection.api()
                val user = async { runCatching { connection.authenticatedUser() }.getOrNull() }
                val dto = api.issue(issueId)
                val item = checkNotNull(dto.toIssueItem(api, titles::get)) { "Unrenderable media type" }
                dto.toDetail(item, user.await(), connection.current().baseUrl)
            }

        private fun SeerrIssueDto.toDetail(
            item: IssueItem,
            user: SeerrUserDto?,
            baseUrl: String,
        ): IssueDetail {
            val permissions = user.toPermissions()
            val comments = comments.sortedBy { it.id }.map { it.toIssueComment(user?.id) }
            val isReporter = user != null && createdBy?.id == user.id
            return IssueDetail(
                item = item,
                report = comments.firstOrNull(),
                comments = comments.drop(1),
                canComment = permissions.canManageIssues || (permissions.canCreateIssues && isReporter),
                canManage = permissions.canManageIssues,
                canResolve = permissions.canManageIssues || (permissions.canCreateIssues && isReporter),
                canDelete = permissions.canManageIssues || (permissions.canCreateIssues && isReporter),
                webUrl = baseUrl + "issues/" + id,
                mediaServerUrl = media?.mediaUrl?.takeIf { it.isWebUrl() },
                serviceUrl = media?.serviceUrl?.takeIf { it.isWebUrl() },
                currentUserName = user?.let { listOfNotNull(it.displayName, it.username).firstOrNull { name -> name.isNotBlank() } },
            )
        }

        private fun ready(): IssueDetailUiState.Ready? = state.value as? IssueDetailUiState.Ready

        private fun outbox(localId: Long): OutboxComment? = ready()?.outbox?.firstOrNull { it.localId == localId }

        private fun updateReady(transform: (IssueDetailUiState.Ready) -> IssueDetailUiState.Ready) =
            state.update { current -> (current as? IssueDetailUiState.Ready)?.let(transform) ?: current }

        private fun updateOutbox(
            localId: Long,
            transform: (OutboxComment) -> OutboxComment,
        ) = updateReady { ready -> ready.copy(outbox = ready.outbox.map { if (it.localId == localId) transform(it) else it }) }

        @AssistedFactory
        interface Factory {
            fun create(issueId: Int): IssueDetailViewModel
        }
    }

private const val STATUS_OPEN = "open"
private const val STATUS_RESOLVED = "resolved"

private fun io.github.scottcooper92.binge.seerr.seerr.SeerrIssueCommentDto.toIssueComment(currentUserId: Int?): IssueComment =
    IssueComment(
        id = id,
        author = user?.displayString(),
        authorId = user?.id,
        isAdmin = SeerrPermissions.fromBits(user?.permissions).isAdmin,
        message = message.orEmpty(),
        createdAtMillis = createdAt?.toEpochMillisOrNull(),
        isMine = user?.id != null && user.id == currentUserId,
    )
