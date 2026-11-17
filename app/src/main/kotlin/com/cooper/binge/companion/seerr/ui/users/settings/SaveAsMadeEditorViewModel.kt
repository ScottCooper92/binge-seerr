package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.editor.EditorState
import com.cooper.homelab.core.presentation.editor.SettingsEditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.binge.designsystem.SaveStatus as UiSaveStatus
import com.cooper.homelab.core.presentation.editor.SaveStatus as EngineSaveStatus

/**
 * A settings page that saves as it changes, on the shared library's engine, shown through the same [uiState] and [events] as
 * any other editor here. A refusal is said through [EditorEvent.Failed] with the server's own reason, as [EditorViewModel]
 * does: this app's rule (a dialog for any reason) is kept, not the library's 40-character one. A write the local-network
 * permission blocked is kept unsent with a retry, as before, so [classify] reports it as unreachable. A write that fails on
 * cleartext or a client certificate is now put back and reported, not kept: it cannot follow a load that worked.
 */
abstract class SaveAsMadeEditorViewModel<T>(
    private val dispatcher: CoroutineDispatcher,
    appScope: CoroutineScope,
) : ViewModel() {
    /** The throwable [classify] last read, for the refusal that follows it: the library's failure keeps no exception. */
    @Volatile private var lastFailure: Throwable? = null

    /** The throwable behind a load that failed, for the error the screen shows. */
    @Volatile private var lastLoadFailure: Throwable? = null

    private val engine =
        SettingsEditorViewModel<T>(
            scope = viewModelScope,
            appScope = appScope,
            dispatcher = dispatcher,
            load = { attempt { load() }.onFailure { lastLoadFailure = it }.getOrThrow() },
            write = ::write,
            classify = ::classify,
            canSave = ::canSave,
        )

    private val ui = MutableStateFlow<EditorUiState<T>>(EditorUiState.Loading)

    /** What the screen shows. It follows the engine at once, so a read right after [edit] sees the edit. */
    val uiState: StateFlow<EditorUiState<T>> = ui.asStateFlow()

    private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

    init {
        viewModelScope.launch(dispatcher) {
            var announced: Any? = null
            engine.state.collect { state ->
                sync()
                val refusal = (state as? EditorState.Ready<T>)?.refusal
                if (refusal != null && refusal !== announced) lastFailure?.let { eventFlow.emit(EditorEvent.Failed.from(it)) }
                announced = refusal
            }
        }
    }

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    private suspend fun classify(failure: Throwable): ServerFailure {
        lastFailure = failure
        return if (failure.toSeerrError() == SeerrError.LocalNetworkDenied) ServerFailure.Unreachable() else failure.toServerFailure()
    }

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

    /** Shows the engine's state as it is now, so a page that acts and reads back sees its own action. */
    private fun sync() {
        // Two threads can call this at once (the page, and the collector below), so a write that finds the engine has moved on
        // since it read redoes itself: the last one to finish always shows the latest state. Compared by value: the library's plain
        // editor builds its state afresh on every read, so identity would never settle.
        do {
            val state = engine.state.value
            ui.value = state.toUiState()
        } while (state != engine.state.value)
    }

    /** Drops a change not yet on the server instead of sending it: the page is about to replace the record from elsewhere. */
    protected fun discardUnsent() = engine.discardUnsent()

    protected fun ready(): EditorUiState.Ready<T>? = uiState.value as? EditorUiState.Ready<T>

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)

    override fun onCleared() = engine.close()

    private fun EditorState<T>.toUiState(): EditorUiState<T> =
        when (this) {
            EditorState.Loading -> EditorUiState.Loading
            is EditorState.Failed -> EditorUiState.Error(lastLoadFailure?.toSeerrError() ?: SeerrError.Unknown)
            is EditorState.Ready ->
                EditorUiState.Ready(
                    draft = draft,
                    saved = saved,
                    saveFailed = saveFailed,
                    saveStatus = saveStatus.toUi(),
                    saveRefusal = refusal?.let { lastFailure?.toSeerrError() },
                )
        }

    private fun EngineSaveStatus.toUi(): UiSaveStatus =
        when (this) {
            EngineSaveStatus.Idle -> UiSaveStatus.Idle
            EngineSaveStatus.Saving -> UiSaveStatus.Saving
            EngineSaveStatus.Saved -> UiSaveStatus.Saved
        }
}
