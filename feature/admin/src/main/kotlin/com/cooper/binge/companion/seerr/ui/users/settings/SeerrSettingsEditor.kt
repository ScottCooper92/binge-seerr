package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.editor.CommitEvent
import com.cooper.homelab.core.presentation.editor.CommitForm
import com.cooper.homelab.core.presentation.editor.CommitState
import com.cooper.homelab.core.presentation.editor.DraftKeeper
import com.cooper.homelab.core.presentation.editor.EditorEvent
import com.cooper.homelab.core.presentation.editor.EditorState
import com.cooper.homelab.core.presentation.editor.SettingsEditor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicReference

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
 * [state] and [events] with the page's own [pageEvents] beside them. The page gives [load] and [write], and [canSave] where a blank
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

    /** What happened to a save, once. */
    val events: Flow<EditorEvent> = editor.events

    /** What happened beside the form, once: see [PageEvent]. */
    val pageEvents: Flow<PageEvent> = page.flow

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
 * and [events] with the page's own [pageEvents] beside them: load a record, edit a draft of it, commit the draft and adopt what the
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

    /** What happened to a commit, once. */
    val events: Flow<CommitEvent> = form.events

    /** What happened beside the form, once: see [PageEvent]. */
    val pageEvents: Flow<PageEvent> = page.flow

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
