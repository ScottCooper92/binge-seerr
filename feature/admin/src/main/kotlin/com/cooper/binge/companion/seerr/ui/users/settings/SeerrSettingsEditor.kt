package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.editor.CommitEvent
import com.cooper.homelab.core.presentation.editor.CommitForm
import com.cooper.homelab.core.presentation.editor.CommitState
import com.cooper.homelab.core.presentation.editor.DraftKeeper
import com.cooper.homelab.core.presentation.editor.EditorState
import com.cooper.homelab.core.presentation.editor.SettingsEditor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import com.cooper.homelab.core.presentation.editor.EditorEvent as LibraryEditorEvent

/**
 * A [SettingsEditor] for one of this app's settings pages: what the library asks of a page, with what a failure means here. A write
 * the local-network permission blocked is kept unsent with a retry ([toEngineFailure]), but a read it blocked is the error the page
 * shows, so the user is sent to the permission and not told the server is unreachable.
 */
internal fun <T, X> seerrSettingsEditor(
    initialExtras: X,
    scope: CoroutineScope,
    appScope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    load: suspend () -> T,
    write: suspend (T) -> T,
    canSave: (T) -> Boolean = { true },
): SettingsEditor<T, X> {
    // The throwable behind a read that failed, so the classifier below can tell it from a write's.
    val loadFailure = AtomicReference<Throwable?>(null)
    return SettingsEditor(
        initialExtras = initialExtras,
        scope = scope,
        appScope = appScope,
        dispatcher = dispatcher,
        load = { attempt { load() }.onFailure { loadFailure.set(it) }.getOrThrow() },
        write = write,
        classify = { failure -> if (failure === loadFailure.get()) failure.toServerFailure() else failure.toEngineFailure() },
        canSave = canSave,
    )
}

/**
 * A settings page that saves as it changes, on the shared library's [SettingsEditor], shown to its screen as the library's own
 * [state] and one stream of [events], the library's and the page's together. The page gives [load] and [write], and [canSave] where a blank
 * field is an error and not a change.
 */
abstract class SettingsEditorViewModel<T, X>(
    initialExtras: X,
    dispatcher: CoroutineDispatcher,
    appScope: CoroutineScope,
) : ViewModel() {
    private val page = PageEvents()

    private val editor = seerrSettingsEditor(initialExtras, viewModelScope, appScope, dispatcher, ::load, ::write, ::canSave)

    /** What the screen shows: the editor's own state. */
    val state: StateFlow<EditorState<T, X>> = editor.state

    /** Everything the page tells the screen once, in one stream: what happened to a save and what happened beside the form. */
    val events: Flow<PageEvent> = page.flow

    init {
        viewModelScope.launch(dispatcher) {
            editor.events.collect { page.send(it.toPageEvent()) }
        }
    }

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    /** Reads the record again, sending a change still waiting first. */
    fun reload() = editor.reload()

    fun edit(transform: (T) -> T) = editor.change(transform)

    /** The retry after a write that failed to reach the server. */
    fun save() = editor.retry()

    protected fun ready(): EditorState.Ready<T, X>? = state.value as? EditorState.Ready<T, X>

    /** What the next ready state adopts extras from; for [write], reached before the record has been read. */
    protected fun currentExtras(): X = editor.extras()

    /** Updates extras alone, leaving the draft untouched: a test's answer, a picked choice, a poll tick. */
    protected fun editExtras(transform: (X) -> X) = editor.changeExtras(transform)

    /** Drops a change not yet on the server instead of sending it: the page is about to replace the record from elsewhere. */
    protected fun discardUnsent() = editor.discardUnsent()

    /** Tells the screen something once, beside the form. */
    protected fun notify(event: PageEvent) = page.send(event)

    override fun onCleared() = editor.close()
}

/**
 * A settings page with an explicit commit, on the shared library's [CommitForm], shown to its screen as the library's own [state]
 * and one stream of [events], the library's and the page's together: load a record, edit a draft of it, commit the draft and adopt what the
 * server answered. The page gives [load] and [write], and [canSave] where a blank field is an error and not a change.
 *
 * @param draftKeeper where the unsaved draft is kept across the process being killed (#1026); null keeps it in memory.
 */
abstract class CommitFormViewModel<T, X>(
    initialExtras: X,
    dispatcher: CoroutineDispatcher,
    draftKeeper: DraftKeeper<T>? = null,
) : ViewModel() {
    private val page = PageEvents()

    private val form =
        CommitForm(
            initialExtras = initialExtras,
            scope = viewModelScope,
            dispatcher = dispatcher,
            load = { attempt { load() }.getOrThrow() },
            write = ::write,
            classify = ::classify,
            canCommit = ::canSave,
            draftKeeper = draftKeeper,
        )

    /** What the screen shows: the form's own state. */
    val state: StateFlow<CommitState<T, X>> = form.state

    /** Everything the page tells the screen once, in one stream: what happened to a commit and what happened beside the form. */
    val events: Flow<PageEvent> = page.flow

    init {
        viewModelScope.launch(dispatcher) {
            form.events.collect { event -> event.toPageEvent(::commitFailed)?.let(page::send) }
        }
    }

    /** The one failure a commit ends with that the page has already told the user about, so it is not said again; null where there is none. */
    protected open val silentCommitFailure: ServerFailure? = null

    private fun commitFailed(failure: ServerFailure): PageEvent? =
        if (failure === silentCommitFailure) null else PageEvent.Failed(failure.toSeerrError())

    protected abstract suspend fun load(): T

    /** Writes [draft] and answers with the record to adopt as saved. */
    protected abstract suspend fun write(draft: T): T

    /** Whether [draft] may be sent; a page overrides this where a blank field is not a change but an error. */
    protected open fun canSave(draft: T): Boolean = true

    /** How a failed commit reads; a page overrides this where one failure needs its own message. */
    protected open suspend fun classify(failure: Throwable): ServerFailure = failure.toServerFailure()

    /** Reads the record again. */
    fun reload() = form.reload()

    fun edit(transform: (T) -> T) = form.edit(transform)

    /** Writes the draft. */
    fun save() = form.commit()

    /** What the next ready state adopts extras from; for [write], reached before the record has been read. */
    protected fun currentExtras(): X = form.extras()

    /** Updates extras alone, leaving the draft untouched: a test's answer, a picked choice, a poll tick. */
    protected fun editExtras(transform: (X) -> X) = form.changeExtras(transform)

    protected fun ready(): CommitState.Ready<T, X>? = state.value as? CommitState.Ready<T, X>

    /** Tells the screen something once, beside the form. */
    protected fun notify(event: PageEvent) = page.send(event)
}

/** The library's save event as the page's own. */
private fun LibraryEditorEvent.toPageEvent(): PageEvent =
    when (this) {
        is LibraryEditorEvent.Refused -> PageEvent.Refused(notice)
        LibraryEditorEvent.SaveFailed -> PageEvent.SaveFailed
    }

/** The library's commit event as the page's own, or null for none; [failed] says what a failure other than a refusal is. */
private fun CommitEvent.toPageEvent(failed: (ServerFailure) -> PageEvent?): PageEvent? =
    when (this) {
        CommitEvent.Committed -> PageEvent.Committed
        is CommitEvent.Refused -> PageEvent.Refused(notice)
        is CommitEvent.Failed -> failed(failure)
    }
