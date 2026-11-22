package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.homelab.core.presentation.editor.ExtrasEditorState
import com.cooper.homelab.core.presentation.editor.SettingsEditorWithExtras
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [SaveAsMadeEditorViewModel] for a page that shows more than the record it edits: [X] sits beside the draft in
 * [ExtrasEditorUiState.Ready], on the shared library's extras slot, so the screen reads one state. A sibling rather than a type
 * parameter on the plain one, so a page with no extras carries nothing extra.
 *
 * @param initialExtras what extras are until a page changes them; they may change while the record is still loading.
 */
abstract class SaveAsMadeEditorWithExtrasViewModel<T, X>(
    initialExtras: X,
    private val dispatcher: CoroutineDispatcher,
    appScope: CoroutineScope,
) : ViewModel() {
    /** The throwable [classify] last read, for the refusal that follows it: the library's failure keeps no exception. */
    @Volatile private var lastFailure: Throwable? = null

    /** The throwable behind a load that failed, for the error the screen shows. */
    @Volatile private var lastLoadFailure: Throwable? = null

    private val engine =
        SettingsEditorWithExtras<T, X>(
            initialExtras = initialExtras,
            scope = viewModelScope,
            appScope = appScope,
            dispatcher = dispatcher,
            load = { attempt { load() }.onFailure { lastLoadFailure = it }.getOrThrow() },
            write = ::write,
            classify = { failure ->
                lastFailure = failure
                failure.toEngineFailure()
            },
            canSave = ::canSave,
        )

    private val ui = MutableStateFlow<ExtrasEditorUiState<T, X>>(ExtrasEditorUiState.Loading)

    /** What the screen shows. It follows the engine at once, so a read right after [edit] or [editExtras] sees the change. */
    val uiState: StateFlow<ExtrasEditorUiState<T, X>> = ui.asStateFlow()

    private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

    init {
        viewModelScope.launch(dispatcher) {
            var announced: Any? = null
            engine.state.collect { state ->
                sync()
                val refusal = (state as? ExtrasEditorState.Ready<T, X>)?.refusal
                if (refusal != null && refusal !== announced) lastFailure?.let { eventFlow.emit(refusal.toRefusedEvent(it)) }
                announced = refusal
            }
        }
    }

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    /** Reads the record again, sending a change still waiting first. */
    fun reload() {
        engine.reload()
        sync()
    }

    fun edit(transform: (T) -> T) {
        engine.change(transform)
        sync()
    }

    /** Drops the refusal a page was showing, as it is left. */
    fun clearRefusal() {
        engine.clearRefusal()
        sync()
    }

    /** The retry after a write that failed to reach the server. */
    fun save() {
        engine.retry()
        sync()
    }

    /** What the next [ExtrasEditorUiState.Ready] adopts extras from; for [write], reached before the record has been read. */
    protected fun currentExtras(): X = engine.extras()

    /** Updates extras alone, leaving the draft untouched: a test's answer, a picked choice, a poll tick. */
    protected fun editExtras(transform: (X) -> X) {
        engine.changeExtras(transform)
        sync()
    }

    /** Changes the saved record and the draft together, by the same function: a page whose record another page wrote part of. */
    protected fun editSaved(transform: (T) -> T) {
        engine.changeSaved(transform)
        sync()
    }

    /** Shows the engine's state as it is now, so a page that acts and reads back sees its own action. */
    private fun sync() {
        // Two threads can call this at once (the page, and the collector above), so a write that finds the engine has moved on
        // since it read redoes itself: the last one to finish always shows the latest state. Compared by value: the library's plain
        // editor builds its state afresh on every read, so identity would never settle.
        do {
            val state = engine.state.value
            ui.value = state.toUiState(lastLoadFailure, lastFailure)
        } while (state != engine.state.value)
    }

    /** Drops a change not yet on the server instead of sending it: the page is about to replace the record from elsewhere. */
    protected fun discardUnsent() = engine.discardUnsent()

    protected fun ready(): ExtrasEditorUiState.Ready<T, X>? = uiState.value as? ExtrasEditorUiState.Ready<T, X>

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)

    override fun onCleared() = engine.close()
}

/** The engine's state as the screens read it. A refusal and a failed load keep their throwable, which the engine's failure does not. */
private fun <T, X> ExtrasEditorState<T, X>.toUiState(
    loadFailure: Throwable?,
    refusedWith: Throwable?,
): ExtrasEditorUiState<T, X> =
    when (this) {
        ExtrasEditorState.Loading -> ExtrasEditorUiState.Loading
        is ExtrasEditorState.Failed -> ExtrasEditorUiState.Error(loadFailure?.toSeerrError() ?: SeerrError.Unknown)
        is ExtrasEditorState.Ready ->
            ExtrasEditorUiState.Ready(
                draft = draft,
                saved = saved,
                extras = extras,
                saveFailed = saveFailed,
                saveStatus = saveStatus.toUi(),
                saveRefusal = refusal?.let { refusedWith?.toSeerrError() },
            )
    }
