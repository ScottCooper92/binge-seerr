package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.homelab.core.presentation.editor.CommitEvent
import com.cooper.homelab.core.presentation.editor.CommitForm
import com.cooper.homelab.core.presentation.editor.CommitState
import com.cooper.homelab.core.presentation.editor.DraftKeeper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A settings page with an explicit commit, on the shared library's [CommitForm], shown through the same [uiState] and [events] as
 * every other editor here: load a record, edit a draft of it, commit the draft and adopt what the server answered. A refused or
 * failed commit keeps the draft and says why through [EditorEvent.Failed]; one that goes through says [EditorEvent.Saved].
 *
 * @param draftKeeper where the unsaved draft is kept across the process being killed (#1026); null keeps it in memory.
 */
abstract class CommitEditorViewModel<T>(
    private val dispatcher: CoroutineDispatcher,
    draftKeeper: DraftKeeper<T>? = null,
) : ViewModel() {
    /** The throwable [CommitForm]'s classifier last read, for the failure that follows it: the library's failure keeps no exception. */
    @Volatile private var lastFailure: Throwable? = null

    /** The throwable behind a load that failed, for the error the screen shows. */
    @Volatile private var lastLoadFailure: Throwable? = null

    private val form =
        CommitForm.plain<T>(
            scope = viewModelScope,
            dispatcher = dispatcher,
            load = { attempt { load() }.onFailure { lastLoadFailure = it }.getOrThrow() },
            write = ::write,
            classify = { failure ->
                lastFailure = failure
                failure.toEngineFailure()
            },
            canCommit = ::canSave,
            draftKeeper = draftKeeper,
        )

    private val ui = MutableStateFlow<EditorUiState<T>>(EditorUiState.Loading)

    /** What the screen shows. It follows the form at once, so a read right after [edit] sees the edit. */
    val uiState: StateFlow<EditorUiState<T>> = ui.asStateFlow()

    private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

    init {
        viewModelScope.launch(dispatcher) {
            launch { form.state.collect { sync() } }
            form.events.collect { event -> eventFlow.emit(event.toEditorEvent(lastFailure)) }
        }
    }

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    /** Reads the record again. */
    fun reload() {
        form.reload()
        sync()
    }

    fun edit(transform: (T) -> T) {
        form.edit(transform)
        sync()
    }

    /** Writes the draft. */
    fun save() {
        form.commit()
        sync()
    }

    /** A page that commits explicitly shows no refusal in place, so there is none to drop; kept so a screen can leave any editor alike. */
    fun clearRefusal() = Unit

    private fun sync() {
        // Two threads can call this at once (the page, and the collector above), so a write that finds the form has moved on since
        // it read redoes itself: the last one to finish always shows the latest state.
        do {
            val state = form.state.value
            ui.value = state.toEditorUiState(lastLoadFailure)
        } while (state != form.state.value)
    }

    protected fun ready(): EditorUiState.Ready<T>? = uiState.value as? EditorUiState.Ready<T>

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)
}

/**
 * [CommitEditorViewModel] for a page that shows more than the record it edits: [X] sits beside the draft in
 * [ExtrasEditorUiState.Ready], on the library's extras slot, so the screen reads one state. A sibling rather than a type
 * parameter on the plain one, so a page with no extras carries nothing extra.
 *
 * @param initialExtras what extras are until a page changes them; they may change while the record is still loading.
 */
abstract class CommitEditorWithExtrasViewModel<T, X>(
    initialExtras: X,
    private val dispatcher: CoroutineDispatcher,
    draftKeeper: DraftKeeper<T>? = null,
) : ViewModel() {
    @Volatile private var lastFailure: Throwable? = null

    @Volatile private var lastLoadFailure: Throwable? = null

    private val form =
        CommitForm<T, X>(
            initialExtras = initialExtras,
            scope = viewModelScope,
            dispatcher = dispatcher,
            load = { attempt { load() }.onFailure { lastLoadFailure = it }.getOrThrow() },
            write = ::write,
            classify = { failure ->
                lastFailure = failure
                failure.toEngineFailure()
            },
            canCommit = ::canSave,
            draftKeeper = draftKeeper,
        )

    private val ui = MutableStateFlow<ExtrasEditorUiState<T, X>>(ExtrasEditorUiState.Loading)

    /** What the screen shows. It follows the form at once, so a read right after [edit] or [editExtras] sees the change. */
    val uiState: StateFlow<ExtrasEditorUiState<T, X>> = ui.asStateFlow()

    private val eventFlow = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditorEvent> = eventFlow.asSharedFlow()

    init {
        viewModelScope.launch(dispatcher) {
            launch { form.state.collect { sync() } }
            form.events.collect { event -> eventFlow.emit(event.toEditorEvent(lastFailure)) }
        }
    }

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    /** Reads the record again. */
    fun reload() {
        form.reload()
        sync()
    }

    fun edit(transform: (T) -> T) {
        form.edit(transform)
        sync()
    }

    /** Writes the draft. */
    fun save() {
        form.commit()
        sync()
    }

    /** As [CommitEditorViewModel.clearRefusal]. */
    fun clearRefusal() = Unit

    /** What the next [ExtrasEditorUiState.Ready] adopts extras from; for [write], reached before the record has been read. */
    protected fun currentExtras(): X = form.extras()

    /** Updates extras alone, leaving the draft untouched: a test's answer, a picked choice, a poll tick. */
    protected fun editExtras(transform: (X) -> X) {
        form.changeExtras(transform)
        sync()
    }

    private fun sync() {
        do {
            val state = form.state.value
            ui.value = state.toExtrasUiState(lastLoadFailure)
        } while (state != form.state.value)
    }

    protected fun ready(): ExtrasEditorUiState.Ready<T, X>? = uiState.value as? ExtrasEditorUiState.Ready<T, X>

    /** For a page's own actions beside the form, which report through the same snackbar as a save. */
    protected suspend fun notify(event: EditorEvent) = eventFlow.emit(event)
}

/** The form's outcome as the screens read it: a commit that went through says Saved, and a refused or failed one says why, as it always did. */
private fun CommitEvent.toEditorEvent(failure: Throwable?): EditorEvent =
    when (this) {
        CommitEvent.Committed -> EditorEvent.Saved
        is CommitEvent.Refused, is CommitEvent.Failed ->
            failure?.let { EditorEvent.Failed.from(it) } ?: EditorEvent.Failed(SeerrError.Unknown)
    }

private fun <T> CommitState<T, Unit>.toEditorUiState(loadFailure: Throwable?): EditorUiState<T> =
    when (this) {
        CommitState.Loading -> EditorUiState.Loading
        is CommitState.Failed -> EditorUiState.Error(loadFailure?.toSeerrError() ?: SeerrError.Unknown)
        is CommitState.Ready -> EditorUiState.Ready(draft = draft, saved = saved, saving = committing)
    }

private fun <T, X> CommitState<T, X>.toExtrasUiState(loadFailure: Throwable?): ExtrasEditorUiState<T, X> =
    when (this) {
        CommitState.Loading -> ExtrasEditorUiState.Loading
        is CommitState.Failed -> ExtrasEditorUiState.Error(loadFailure?.toSeerrError() ?: SeerrError.Unknown)
        is CommitState.Ready -> ExtrasEditorUiState.Ready(draft = draft, saved = saved, extras = extras, saving = committing)
    }
