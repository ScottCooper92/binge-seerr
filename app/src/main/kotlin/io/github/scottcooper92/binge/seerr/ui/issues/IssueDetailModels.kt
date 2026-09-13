package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.seerr.SeerrError

/** One comment as the server holds it; [isMine] is the connected user's, which is what gates its actions. */
data class IssueComment(
    val id: Int,
    val author: String?,
    val authorId: Int?,
    val isAdmin: Boolean,
    val message: String,
    val createdAtMillis: Long?,
    val isMine: Boolean,
)

/**
 * One issue as a page. The report is the thread's first comment; [comments] are the rest. The
 * user may comment as a manager or as the reporter, and act on a comment as a manager or its author.
 */
data class IssueDetail(
    val item: IssueItem,
    val report: IssueComment?,
    val comments: List<IssueComment>,
    val canComment: Boolean,
    val canManage: Boolean,
    /** The issue in the server's web client, for the hand-off. */
    val webUrl: String,
    val mediaServerUrl: String?,
    val serviceUrl: String?,
    /** The connected user's name, which a pending comment is shown under. */
    val currentUserName: String?,
) {
    fun canActOn(comment: IssueComment): Boolean = canManage || comment.isMine
}

/** Where a comment the user submitted is in its send. */
sealed interface SendState {
    data object Sending : SendState

    /** [retryable] for a failure a re-send may clear; false for a rejection a re-send would only repeat. */
    data class Failed(
        val retryable: Boolean,
    ) : SendState
}

/** A comment the user submitted that the server has not yet confirmed, keyed by a local id until it lands. */
data class OutboxComment(
    val localId: Long,
    val message: String,
    val author: String?,
    val submittedAtMillis: Long,
    val state: SendState,
)

/** One rendered thread entry: a server comment, or a pending one pinned to the bottom in submit order. */
sealed interface ThreadEntry {
    /** Stable across recompositions: the server id, or the negated local id so the two can never collide. */
    val key: Long

    data class Server(
        val comment: IssueComment,
    ) : ThreadEntry {
        override val key: Long get() = comment.id.toLong()
    }

    data class Pending(
        val outbox: OutboxComment,
    ) : ThreadEntry {
        override val key: Long get() = -outbox.localId
    }
}

/** The one per-comment write in flight, naming the comment so only its row is held. */
sealed interface CommentAction {
    val commentId: Int?

    data object None : CommentAction {
        override val commentId: Int? get() = null
    }

    data class Editing(
        override val commentId: Int,
    ) : CommentAction

    data class Deleting(
        override val commentId: Int,
    ) : CommentAction
}

sealed interface IssueDetailUiState {
    data object Loading : IssueDetailUiState

    data class Ready(
        val detail: IssueDetail,
        val draft: String = "",
        val outbox: List<OutboxComment> = emptyList(),
        val commentAction: CommentAction = CommentAction.None,
    ) : IssueDetailUiState {
        val thread: List<ThreadEntry>
            get() = detail.comments.map { ThreadEntry.Server(it) } + outbox.map { ThreadEntry.Pending(it) }
    }

    data class Error(
        val error: SeerrError,
    ) : IssueDetailUiState
}

/** A write's outcome, for the snackbar; posting is silent, since the pending row is its own feedback. */
sealed interface IssueDetailEvent {
    data object CommentEdited : IssueDetailEvent

    data object CommentDeleted : IssueDetailEvent

    data class Failed(
        val error: SeerrError,
    ) : IssueDetailEvent
}
