package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue

/**
 * Which of the page's sheets is open and its draft, restored through [Saver] so an open composer
 * and its typed text survive a rotation.
 */
@Stable
internal class IssueModalState {
    var composing by mutableStateOf(false)
    var actingOnCommentId by mutableStateOf<Int?>(null)
    var editingCommentId by mutableStateOf<Int?>(null)
    var editDraft by mutableStateOf("")
    var deletingCommentId by mutableStateOf<Int?>(null)
    var outboxActionId by mutableStateOf<Long?>(null)
    var editingOutboxId by mutableStateOf<Long?>(null)
    var outboxEditDraft by mutableStateOf("")

    fun openEdit(comment: IssueComment) {
        actingOnCommentId = null
        editingCommentId = comment.id
        editDraft = comment.message
    }

    fun openOutboxEdit(entry: OutboxComment) {
        editingOutboxId = entry.localId
        outboxEditDraft = entry.message
    }

    companion object {
        private const val KEY_COMPOSING = "composing"
        private const val KEY_ACTING = "acting"
        private const val KEY_EDITING = "editing"
        private const val KEY_EDIT_DRAFT = "editDraft"
        private const val KEY_DELETING = "deleting"
        private const val KEY_OUTBOX_ACTION = "outboxAction"
        private const val KEY_EDITING_OUTBOX = "editingOutbox"
        private const val KEY_OUTBOX_DRAFT = "outboxDraft"

        val Saver: Saver<IssueModalState, Any> =
            mapSaver(
                save = { state ->
                    mapOf(
                        KEY_COMPOSING to state.composing,
                        KEY_ACTING to state.actingOnCommentId,
                        KEY_EDITING to state.editingCommentId,
                        KEY_EDIT_DRAFT to state.editDraft,
                        KEY_DELETING to state.deletingCommentId,
                        KEY_OUTBOX_ACTION to state.outboxActionId,
                        KEY_EDITING_OUTBOX to state.editingOutboxId,
                        KEY_OUTBOX_DRAFT to state.outboxEditDraft,
                    )
                },
                restore = { saved ->
                    IssueModalState().apply {
                        composing = saved[KEY_COMPOSING] as Boolean
                        actingOnCommentId = saved[KEY_ACTING] as Int?
                        editingCommentId = saved[KEY_EDITING] as Int?
                        editDraft = saved[KEY_EDIT_DRAFT] as String
                        deletingCommentId = saved[KEY_DELETING] as Int?
                        outboxActionId = saved[KEY_OUTBOX_ACTION] as Long?
                        editingOutboxId = saved[KEY_EDITING_OUTBOX] as Long?
                        outboxEditDraft = saved[KEY_OUTBOX_DRAFT] as String
                    }
                },
            )
    }
}
