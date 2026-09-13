package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A form over one server record: what was loaded, what the user has typed, and whether a save is in flight. */
sealed interface EditorUiState<out T> {
    data object Loading : EditorUiState<Nothing>

    data class Ready<T>(
        val draft: T,
        val saved: T,
        val saving: Boolean = false,
    ) : EditorUiState<T> {
        val dirty: Boolean get() = draft != saved
    }

    data class Error(
        val error: SeerrError,
    ) : EditorUiState<Nothing>
}

sealed interface EditorEvent {
    data object Saved : EditorEvent

    data class Failed(
        val error: SeerrError,
    ) : EditorEvent

    /** An action beside the form succeeded; [messageRes] says which. */
    data class Notice(
        @StringRes val messageRes: Int,
    ) : EditorEvent
}

/**
 * The shape every per-user settings page shares: load a record, edit a draft of it, save the
 * draft and adopt what the server answered as the new baseline. A page supplies the two calls
 * and, where the server's answer is not the record, what to adopt.
 */
abstract class EditorViewModel<T> : ViewModel() {
    private val state = MutableStateFlow<EditorUiState<T>>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState<T>> = state.asStateFlow()

    private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    fun reload() {
        state.value = EditorUiState.Loading
        viewModelScope.launch {
            runCatching { load() }
                .onSuccess { state.value = EditorUiState.Ready(draft = it, saved = it) }
                .onFailure { state.value = EditorUiState.Error(it.toSeerrError()) }
        }
    }

    fun edit(transform: (T) -> T) =
        state.update { current ->
            val ready = current as? EditorUiState.Ready<T> ?: return@update current
            if (ready.saving) ready else ready.copy(draft = transform(ready.draft))
        }

    fun save() {
        val ready = state.value as? EditorUiState.Ready<T> ?: return
        if (ready.saving || !ready.dirty || !canSave(ready.draft)) return
        state.value = ready.copy(saving = true)
        viewModelScope.launch {
            runCatching { write(ready.draft) }
                .onSuccess { adopted ->
                    state.value = EditorUiState.Ready(draft = adopted, saved = adopted)
                    eventFlow.emit(EditorEvent.Saved)
                }.onFailure { failure ->
                    state.update { current -> (current as? EditorUiState.Ready<T>)?.copy(saving = false) ?: current }
                    eventFlow.emit(EditorEvent.Failed(failure.toSeerrError()))
                }
        }
    }

    protected fun ready(): EditorUiState.Ready<T>? = state.value as? EditorUiState.Ready<T>

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)
}
