package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.homelab.core.presentation.editor.EditorState
import com.cooper.homelab.core.presentation.editor.SettingsEditor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.cooper.homelab.core.presentation.editor.EditorEvent as EngineEvent

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

    /** Why the server refused the last change, kept for the screens that show it in place until the next change or [clearRefusal]. */
    @Volatile private var refusal: SeerrError? = null

    private val engine =
        SettingsEditor<T, X>(
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
            launch { engine.state.collect { sync() } }
            engine.events.collect { event ->
                if (event is EngineEvent.Refused) {
                    lastFailure?.let {
                        refusal = it.toSeerrError()
                        sync()
                        eventFlow.emit(event.notice.toRefusedEvent(it))
                    }
                }
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
        refusal = null
        engine.reload()
        sync()
    }

    fun edit(transform: (T) -> T) {
        val before = (engine.state.value as? EditorState.Ready<T, *>)?.draft
        engine.change(transform)
        if ((engine.state.value as? EditorState.Ready<T, *>)?.draft != before) refusal = null
        sync()
    }

    /** Drops the refusal a page was showing, as it is left. */
    fun clearRefusal() {
        refusal = null
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
            ui.value = state.toUiState(lastLoadFailure, refusal)
        } while (state != engine.state.value)
    }

    /** Drops a change not yet on the server instead of sending it: the page is about to replace the record from elsewhere. */
    protected fun discardUnsent() = engine.discardUnsent()

    protected fun ready(): ExtrasEditorUiState.Ready<T, X>? = uiState.value as? ExtrasEditorUiState.Ready<T, X>

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)

    override fun onCleared() = engine.close()
}

/** The engine's state as the screens read it. A failed load keeps its throwable, which the engine's failure does not, and a refusal is the one the page was told. */
private fun <T, X> EditorState<T, X>.toUiState(
    loadFailure: Throwable?,
    refused: SeerrError?,
): ExtrasEditorUiState<T, X> =
    when (this) {
        EditorState.Loading -> ExtrasEditorUiState.Loading
        is EditorState.Failed -> ExtrasEditorUiState.Error(loadFailure?.toSeerrError() ?: SeerrError.Unknown)
        is EditorState.Ready ->
            ExtrasEditorUiState.Ready(
                draft = draft,
                saved = saved,
                extras = extras,
                saveFailed = saveFailed,
                saveStatus = saveStatus.toUi(),
                saveRefusal = refused,
            )
    }
