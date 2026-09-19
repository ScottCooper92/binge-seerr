package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineDispatcher
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

    /** The record is gone from the server, so the page that edited it has nothing left to show. */
    data object Deleted : EditorEvent

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

    fun reload() {
        state.value = EditorUiState.Loading
        viewModelScope.launch(dispatcher) {
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
        viewModelScope.launch(dispatcher) {
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
    ) : ExtrasEditorUiState<T, X> {
        val dirty: Boolean get() = draft != saved
    }

    data class Error(
        val error: SeerrError,
    ) : ExtrasEditorUiState<Nothing, Nothing>
}

/** [ExtrasEditorUiState] as the plain [EditorUiState] [EditorPage] renders, dropping the extras it has no use for. */
internal fun <T, X> ExtrasEditorUiState<T, X>.toEditorUiState(): EditorUiState<T> =
    when (this) {
        ExtrasEditorUiState.Loading -> EditorUiState.Loading
        is ExtrasEditorUiState.Ready -> EditorUiState.Ready(draft = draft, saved = saved, saving = saving)
        is ExtrasEditorUiState.Error -> EditorUiState.Error(error)
    }

/**
 * [EditorViewModel]'s shape for a page whose extras ([X]) are picked from what the server answers
 * beside the draft — the DVR instance and override rule editors, whose choices come from a test
 * reaching the instance. [initialExtras] is what a fresh [reload] starts from, and what [editExtras]
 * called while [load] is still running — before a [ExtrasEditorUiState.Ready] exists to hold it — is
 * applied to; [currentExtras] reads that same value from [write], which runs in the same window.
 *
 * [dispatcher] carries [reload] and [save] instead of `viewModelScope`'s own
 * `Dispatchers.Main.immediate` — see `RequestModeration`'s KDoc and #177/#370.
 */
abstract class ExtrasEditorViewModel<T, X>(
    initialExtras: X,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow<ExtrasEditorUiState<T, X>>(ExtrasEditorUiState.Loading)
    val uiState: StateFlow<ExtrasEditorUiState<T, X>> = state.asStateFlow()

    private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

    private var extras: X = initialExtras

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    fun reload() {
        state.value = ExtrasEditorUiState.Loading
        viewModelScope.launch(dispatcher) {
            runCatching { load() }
                .onSuccess { state.value = ExtrasEditorUiState.Ready(draft = it, saved = it, extras = extras) }
                .onFailure { state.value = ExtrasEditorUiState.Error(it.toSeerrError()) }
        }
    }

    fun edit(transform: (T) -> T) =
        state.update { current ->
            val ready = current as? ExtrasEditorUiState.Ready<T, X> ?: return@update current
            if (ready.saving) ready else ready.copy(draft = transform(ready.draft))
        }

    fun save() {
        val ready = state.value as? ExtrasEditorUiState.Ready<T, X> ?: return
        if (ready.saving || !ready.dirty || !canSave(ready.draft)) return
        state.value = ready.copy(saving = true)
        viewModelScope.launch(dispatcher) {
            runCatching { write(ready.draft) }
                .onSuccess { adopted ->
                    state.value = ExtrasEditorUiState.Ready(draft = adopted, saved = adopted, extras = extras)
                    eventFlow.emit(EditorEvent.Saved)
                }.onFailure { failure ->
                    state.update { current -> (current as? ExtrasEditorUiState.Ready<T, X>)?.copy(saving = false) ?: current }
                    eventFlow.emit(EditorEvent.Failed(failure.toSeerrError()))
                }
        }
    }

    protected fun ready(): ExtrasEditorUiState.Ready<T, X>? = state.value as? ExtrasEditorUiState.Ready<T, X>

    /** What the next [ExtrasEditorUiState.Ready] adopts extras from; for [write], reached while still [saving]. */
    protected fun currentExtras(): X = extras

    /** Updates extras alone, leaving the draft untouched — a test's answer, a picked choice, a poll tick. */
    protected fun editExtras(transform: (X) -> X) {
        extras = transform(extras)
        state.update { current ->
            val ready = current as? ExtrasEditorUiState.Ready<T, X> ?: return@update current
            ready.copy(extras = extras)
        }
    }

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)
}
