package com.cooper.binge.companion.seerr.ui.issues

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.users.settings.UserRole
import com.cooper.binge.companion.seerr.ui.users.userRole

/** One comment as the server holds it; [isMine] is the connected user's, which is what gates its actions. */
data class IssueComment(
    val id: Int,
    val author: String?,
    val authorId: Int?,
    val isAdmin: Boolean,
    val message: String,
    val createdAtMillis: Long?,
    val isMine: Boolean,
) {
    val role: UserRole get() = userRole(authorId, isAdmin)
}

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
    /** Closing, reopening and deleting: a manager's, or the reporter's on their own issue. */
    val canResolve: Boolean,
    val canDelete: Boolean,
    /** The issue in the server's web client, for the hand-off. */
    val webUrl: String,
    val mediaServerUrl: String?,
    val serviceUrl: String?,
    /** The connected user's name, which a pending comment is shown under. */
    val currentUserName: String?,
    /** What the connected server calls itself: Seerr, Jellyseerr or Overseerr. */
    val serverName: String = "Seerr",
    /** Plex, Jellyfin or Emby; null where the server's type is not known. */
    val mediaServerName: String? = null,
    /** PROTOTYPE: the title's item id on a Jellyfin or Emby server. */
    val mediaServerItemId: String? = null,
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

/** The one issue-level write in flight, so a second tap is swallowed and the control spins. */
enum class IssueAction { None, UpdatingStatus, Deleting }

sealed interface IssueDetailUiState {
    data object Loading : IssueDetailUiState

    /**
     * The row the user tapped, shown while the issue loads: everything the header draws is on it, but
     * the report, the thread and what the viewer may do are not, so none of those are guessed. A failed
     * refresh sets [error] and leaves the header up.
     */
    data class Seeded(
        val item: IssueItem,
        val error: SeerrError? = null,
    ) : IssueDetailUiState

    data class Ready(
        val detail: IssueDetail,
        val draft: String = "",
        val outbox: List<OutboxComment> = emptyList(),
        val commentAction: CommentAction = CommentAction.None,
        val action: IssueAction = IssueAction.None,
        /** What the thread's relative times are worded against: moved on each minute while the page shows (#1239). */
        val now: Long = System.currentTimeMillis(),
        /** An edit to a comment saves as it is made (#930): the page's Saving and Saved. */
        val editStatus: SaveStatus = SaveStatus.Idle,
        /** Edits whose write failed, one per comment, kept on the page and sent again by Retry, in turn, until each lands. */
        val unsentEdits: List<CommentEdit> = emptyList(),
    ) : IssueDetailUiState {
        val thread: List<ThreadEntry>
            get() = detail.comments.map { ThreadEntry.Server(it) } + outbox.map { ThreadEntry.Pending(it) }
    }

    data class Error(
        val error: SeerrError,
    ) : IssueDetailUiState
}

/** A comment's new text, as an edit sends it. */
data class CommentEdit(
    val commentId: Int,
    val message: String,
)

/** The text of comment [id], the report included; null if the thread has no such comment. */
internal fun IssueDetail.messageOf(id: Int): String? = (listOfNotNull(report) + comments).firstOrNull { it.id == id }?.message

/** [edit]'s text in place of the comment it names, the report included. */
internal fun IssueDetail.withEdit(edit: CommentEdit): IssueDetail {
    fun IssueComment.edited() = if (id == edit.commentId) copy(message = edit.message) else this
    return copy(report = report?.edited(), comments = comments.map { it.edited() })
}

/**
 * A write's outcome, for the snackbar. Posting is silent, since the pending row is its own feedback, and so is an edit,
 * which the page's Saving and Saved report (#930).
 */
sealed interface IssueDetailEvent {
    data object CommentDeleted : IssueDetailEvent

    data object IssueResolved : IssueDetailEvent

    data object IssueReopened : IssueDetailEvent

    /** The issue is gone, so the page pops rather than reporting over a surface it is leaving. */
    data object IssueDeleted : IssueDetailEvent

    data class Failed(
        val error: SeerrError,
    ) : IssueDetailEvent
}
