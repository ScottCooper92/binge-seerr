package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.save.SaveAsMade
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.isTransient
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
        /** A page that saves as it changes could not write its last change, which is kept, unsent. */
        val saveFailed: Boolean = false,
        val saveStatus: SaveStatus = SaveStatus.Idle,
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

/**
 * The shape every per-user settings page shares: load a record, edit a draft of it, save the
 * draft and adopt what the server answered as the new baseline. A page supplies the two calls
 * and, where the server's answer is not the record, what to adopt.
 *
 * [dispatcher] carries [reload] and [save] instead of `viewModelScope`'s own
 * `Dispatchers.Main.immediate` — see `RequestModeration`'s KDoc and #177/#370.
 */
abstract class EditorViewModel<T>(
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow<EditorUiState<T>>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState<T>> = state.asStateFlow()

    private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    /**
     * A page that saves as it changes (#930) returns the application's scope here, which the writes run on; null keeps the
     * page on its Save. See [SaveAsMade].
     */
    protected open val saveAsMadeScope: CoroutineScope? = null

    /** Where an explicit-save page keeps its unsaved draft across the process being killed (#1026); null keeps it in memory. */
    protected open val savedDraft: SavedDraft<T>? = null

    /**
     * What a page that saves as it changes may send (#957). A write is the saved record with the user's change in it, so a
     * record the server sent that already fails [canSave] (a required email it left blank, say) goes back as it came rather
     * than holding every change on the page. Only a change that turns a valid record invalid is held.
     */
    private fun canSaveAsMade(draft: T): Boolean = canSave(draft) || ready()?.saved?.let(::canSave) == false

    private val saveAsMade: SaveAsMade<T>? by lazy {
        saveAsMadeScope?.let { appScope ->
            SaveAsMade(
                scope = viewModelScope,
                appScope = appScope,
                dispatcher = dispatcher,
                draft = { ready()?.draft },
                canSave = ::canSaveAsMade,
                write = ::write,
                adopt = { sent, adopted ->
                    var moved = false
                    state.update { current ->
                        val ready = current as? EditorUiState.Ready<T> ?: return@update current
                        moved = ready.draft != sent
                        ready.copy(saved = adopted, draft = if (moved) ready.draft else adopted)
                    }
                    moved
                },
                failed = { failed ->
                    state.update { current -> (current as? EditorUiState.Ready<T>)?.copy(saveFailed = failed) ?: current }
                },
                status = { status ->
                    state.update { current -> (current as? EditorUiState.Ready<T>)?.copy(saveStatus = status) ?: current }
                },
                isTransient = { it.toSeerrError().isTransient },
                refused = { failure ->
                    val error = failure.toSeerrError()
                    state.update { current -> (current as? EditorUiState.Ready<T>)?.let { it.copy(draft = it.saved) } ?: current }
                    eventFlow.emit(EditorEvent.Failed(error))
                },
            )
        }
    }

    /**
     * On a page that saves as it changes, drops a change not yet on the server instead of sending it ([SaveAsMade.discard]):
     * the page is about to replace the record from elsewhere (#1019). Call it on the main thread, before the replacement.
     */
    protected fun discardUnsent() {
        saveAsMade?.discard()
    }

    /**
     * Reads the record again. On a page that saves as it changes, a change not yet on the server goes first ([SaveAsMade.settle]),
     * and if it cannot, the draft is kept over what was read, with the failure showing.
     */
    fun reload() {
        val settling = saveAsMade?.settle()
        state.value = EditorUiState.Loading
        viewModelScope.launch(dispatcher) {
            val kept = settling?.await()
            saveAsMade?.reloaded(keptUnsent = kept != null)
            attempt { load() }
                .onSuccess { loaded ->
                    // A draft kept across the process being killed goes back over what was read (#1026).
                    val draft = kept ?: savedDraft?.restoreOver(loaded) ?: loaded
                    state.value = EditorUiState.Ready(draft = draft, saved = loaded, saveFailed = kept != null)
                }.onFailure { state.value = EditorUiState.Error(it.toSeerrError()) }
        }
    }

    fun edit(transform: (T) -> T) {
        val before = ready()?.draft
        state.update { current ->
            val ready = current as? EditorUiState.Ready<T> ?: return@update current
            if (ready.saving) ready else ready.copy(draft = transform(ready.draft))
        }
        if (ready()?.draft != before) saveAsMade?.changed()
        ready()?.let { savedDraft?.keep(it.draft.takeIf { draft -> draft != it.saved }) }
    }

    /** Writes the draft; on a page that saves as it changes, this is the retry after a write that failed. */
    fun save() {
        saveAsMade?.let {
            it.now()
            return
        }
        val ready = state.value as? EditorUiState.Ready<T> ?: return
        if (ready.saving || !ready.dirty || !canSave(ready.draft)) return
        state.value = ready.copy(saving = true)
        viewModelScope.launch(dispatcher) {
            attempt { write(ready.draft) }
                .onSuccess { adopted ->
                    state.value = EditorUiState.Ready(draft = adopted, saved = adopted)
                    savedDraft?.keep(null)
                    eventFlow.emit(EditorEvent.Saved)
                }.onFailure { failure ->
                    state.update { current -> (current as? EditorUiState.Ready<T>)?.copy(saving = false) ?: current }
                    eventFlow.emit(EditorEvent.Failed(failure.toSeerrError()))
                }
        }
    }

    protected fun ready(): EditorUiState.Ready<T>? = state.value as? EditorUiState.Ready<T>

    override fun onCleared() {
        saveAsMade?.cleared()
    }

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)
}
