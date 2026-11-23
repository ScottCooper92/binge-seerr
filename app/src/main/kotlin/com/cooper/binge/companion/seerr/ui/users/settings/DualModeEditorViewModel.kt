package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.editor.CommitEvent
import com.cooper.homelab.core.presentation.editor.CommitForm
import com.cooper.homelab.core.presentation.editor.CommitState
import com.cooper.homelab.core.presentation.editor.DraftKeeper
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
 * An editor for a record that is new or existing, on the shared library's editors: a new record commits explicitly, through a
 * [CommitForm], and an existing one saves as it changes, through a [SettingsEditor] on [appScope]. A page passes [appScope]
 * only for an existing record, and [draftKeeper] only for a new one, which is the one with an unsaved draft to keep (#1026).
 * Either way the screen reads one [uiState] and one [events], with [extras][X] beside the draft.
 *
 * @param initialExtras what extras are until a page changes them; they may change while the record is still loading.
 * @param appScope the application's scope, which an existing record's writes run on (#930); null commits explicitly.
 * @param draftKeeper where a new record's unsaved draft is kept across the process being killed; null keeps it in memory.
 */
abstract class DualModeEditorViewModel<T, X>(
    initialExtras: X,
    private val dispatcher: CoroutineDispatcher,
    appScope: CoroutineScope?,
    protected val draftKeeper: DraftKeeper<T>? = null,
) : ViewModel() {
    /** The throwable [classify][toEngineFailure] last read, for the event that follows it: the library's failure keeps no exception. */
    @Volatile private var lastFailure: Throwable? = null

    /** The throwable behind a load that failed, for the error the screen shows. */
    @Volatile private var lastLoadFailure: Throwable? = null

    /** Why the server refused the last change to an existing record, kept for the screens that show it in place until the next change. */
    @Volatile private var refusal: SeerrError? = null

    private val loadRecord: suspend () -> T = { attempt { load() }.onFailure { lastLoadFailure = it }.getOrThrow() }

    private val classifyFailure: suspend (Throwable) -> ServerFailure = { failure ->
        lastFailure = failure
        failure.toEngineFailure()
    }

    private val form: CommitForm<T, X>? =
        if (appScope != null) {
            null
        } else {
            CommitForm(
                initialExtras = initialExtras,
                scope = viewModelScope,
                dispatcher = dispatcher,
                load = loadRecord,
                write = ::write,
                classify = classifyFailure,
                canCommit = ::canSave,
                draftKeeper = draftKeeper,
            )
        }

    private val engine: SettingsEditor<T, X>? =
        appScope?.let {
            SettingsEditor(
                initialExtras = initialExtras,
                scope = viewModelScope,
                appScope = it,
                dispatcher = dispatcher,
                load = loadRecord,
                write = ::write,
                classify = classifyFailure,
                canSave = ::canSave,
            )
        }

    private val ui = MutableStateFlow<ExtrasEditorUiState<T, X>>(ExtrasEditorUiState.Loading)

    /** What the screen shows. It follows the editor at once, so a read right after [edit] or [editExtras] sees the change. */
    val uiState: StateFlow<ExtrasEditorUiState<T, X>> = ui.asStateFlow()

    private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

    init {
        viewModelScope.launch(dispatcher) {
            form?.let { commit ->
                launch { commit.state.collect { sync() } }
                commit.events.collect { eventFlow.emit(it.toEditorEvent(lastFailure)) }
            }
            engine?.let { made ->
                launch { made.state.collect { sync() } }
                made.events.collect { event ->
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
    }

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    /** Reads the record again; an existing record's change still waiting is sent first. */
    fun reload() {
        refusal = null
        form?.reload()
        engine?.reload()
        sync()
    }

    fun edit(transform: (T) -> T) {
        val before = ready()?.draft
        form?.edit(transform)
        engine?.change(transform)
        if (ready()?.draft != before) refusal = null
        sync()
    }

    /** Drops the refusal a page was showing, as it is left. */
    fun clearRefusal() {
        refusal = null
        sync()
    }

    /** Commits a new record; for an existing one, the retry after a write that failed to reach the server. */
    fun save() {
        form?.commit()
        engine?.retry()
        sync()
    }

    /** What the next [ExtrasEditorUiState.Ready] adopts extras from; for [write], reached before the record has been read. */
    protected fun currentExtras(): X = form?.extras() ?: checkNotNull(engine).extras()

    /** Updates extras alone, leaving the draft untouched: a test's answer, a picked choice, a poll tick. */
    protected fun editExtras(transform: (X) -> X) {
        form?.changeExtras(transform)
        engine?.changeExtras(transform)
        sync()
    }

    /** Drops an existing record's change not yet on the server instead of sending it: the page replaces the record from elsewhere. */
    protected fun discardUnsent() {
        engine?.discardUnsent()
    }

    private fun sync() {
        // Two threads can call this at once (the page, and the collectors above), so a write that finds the editor has moved on
        // since it read redoes itself: the last one to finish always shows the latest state.
        do {
            val committed = form?.state?.value
            val made = engine?.state?.value
            ui.value =
                committed?.toUiState(lastLoadFailure) ?: made?.toUiState(lastLoadFailure, refusal) ?: ExtrasEditorUiState.Loading
        } while (committed != form?.state?.value || made != engine?.state?.value)
    }

    protected fun ready(): ExtrasEditorUiState.Ready<T, X>? = uiState.value as? ExtrasEditorUiState.Ready<T, X>

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)

    override fun onCleared() {
        engine?.close()
    }
}

private fun <T, X> CommitState<T, X>.toUiState(loadFailure: Throwable?): ExtrasEditorUiState<T, X> =
    when (this) {
        CommitState.Loading -> ExtrasEditorUiState.Loading
        is CommitState.Failed -> ExtrasEditorUiState.Error(loadFailure?.toSeerrError() ?: SeerrError.Unknown)
        is CommitState.Ready -> ExtrasEditorUiState.Ready(draft = draft, saved = saved, extras = extras, saving = committing)
    }

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
