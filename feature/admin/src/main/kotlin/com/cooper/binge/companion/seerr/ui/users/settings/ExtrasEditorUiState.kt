package com.cooper.binge.companion.seerr.ui.users.settings

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.seerr.SeerrError

/**
 * [EditorUiState] for the editors whose page renders more than the record it edits — a test's
 * answer, a picker's own choices — so [Ready] carries that alongside the draft in the same slot
 * rather than a second stream the page would have to combine itself and could render against
 * stale extras. A sibling of [EditorUiState] rather than a type parameter added to it: the other
 * seventeen editors have no extras and gain nothing from carrying an unused slot.
 */
sealed interface ExtrasEditorUiState<out T, out X> {
    data object Loading : ExtrasEditorUiState<Nothing, Nothing>

    data class Ready<T, X>(
        val draft: T,
        val saved: T,
        val extras: X,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val saveStatus: SaveStatus = SaveStatus.Idle,
        /** As [EditorUiState.Ready.saveRefusal]. */
        val saveRefusal: SeerrError? = null,
    ) : ExtrasEditorUiState<T, X> {
        val dirty: Boolean get() = draft != saved
    }

    data class Error(
        val error: SeerrError,
    ) : ExtrasEditorUiState<Nothing, Nothing>
}

/** [ExtrasEditorUiState] as the plain [EditorUiState] [EditorPage] renders, dropping the extras it has no use for. */
fun <T, X> ExtrasEditorUiState<T, X>.toEditorUiState(): EditorUiState<T> =
    when (this) {
        ExtrasEditorUiState.Loading -> EditorUiState.Loading
        is ExtrasEditorUiState.Ready ->
            EditorUiState.Ready(
                draft = draft,
                saved = saved,
                saving = saving,
                saveFailed = saveFailed,
                saveStatus = saveStatus,
                saveRefusal = saveRefusal,
            )
        is ExtrasEditorUiState.Error -> EditorUiState.Error(error)
    }
