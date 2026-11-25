package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.annotation.StringRes
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.feature.admin.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.isMediaServerUnreachable
import com.cooper.binge.companion.seerr.seerr.rejectionDetail
import com.cooper.binge.companion.seerr.seerr.toSeerrError

/** A form over one server record: what was loaded, what the user has typed, and whether a save is in flight. */
sealed interface EditorUiState<out T> {
    data object Loading : EditorUiState<Nothing>

    data class Ready<T>(
        val draft: T,
        val saved: T,
        val saving: Boolean = false,
        /** A page that saves as it changes could not write its last change, which is kept, unsent. */
        val saveFailed: Boolean = false,
        val saveStatus: SaveStatus = SaveStatus.Idle,
        /** Why the server refused the last change, which was put back. It stays until the next change, or [DualModeEditorViewModel.clearRefusal]. */
        val saveRefusal: SeerrError? = null,
    ) : EditorUiState<T> {
        val dirty: Boolean get() = draft != saved
    }

    data class Error(
        val error: SeerrError,
    ) : EditorUiState<Nothing>
}

sealed interface EditorEvent {
    data object Saved : EditorEvent

    /** The record is gone from the server, so the page that edited it has nothing left to show. */
    data object Deleted : EditorEvent

    data class Failed(
        val error: SeerrError,
        /** What the server said it refused, when it rejected the action on the merits and said why. */
        val detail: String? = null,
        /** A sentence of the app's own for a failure the generic one for [error] would misname; it replaces that one. */
        @StringRes val messageRes: Int? = null,
    ) : EditorEvent {
        companion object {
            /** The failure of [failure], with the server's own reason where it gave one: the one way to build it from a throwable. */
            fun from(failure: Throwable) =
                Failed(
                    failure.toSeerrError(),
                    failure.rejectionDetail(),
                    R.string.server_settings_media_server_unreachable.takeIf { failure.isMediaServerUnreachable() },
                )
        }
    }

    /** A test the server could not carry out, and why: a dialog, since the reason is the server's and has no length bound. */
    data class TestFailed(
        val reason: String,
    ) : EditorEvent

    /** An action beside the form succeeded; [messageRes] says which. */
    data class Notice(
        @StringRes val messageRes: Int,
    ) : EditorEvent

    /**
     * A change on a page that saves as it changes, which the snackbar offers to take back (#940): [messageRes] formatted
     * with [argRes]'s text, and [undo] to call when the user asks.
     */
    class Undoable(
        @StringRes val messageRes: Int,
        @StringRes val argRes: Int,
        val undo: () -> Unit,
    ) : EditorEvent
}
