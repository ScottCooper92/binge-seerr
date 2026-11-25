package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.homelab.core.presentation.editor.EditorEvent
import com.cooper.homelab.core.presentation.editor.EditorState
import com.cooper.homelab.core.uiandroid.editor.RefusalNoticeHost
import com.cooper.homelab.core.uiandroid.editor.rememberRefusalNoticeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * [EditorPage] for a page that saves as it changes on the shared library's [com.cooper.homelab.core.presentation.editor.SettingsEditor]:
 * it reads the editor's own [EditorState] and [EditorEvent]s, so the page needs no adapter between them. A refusal is the library's
 * [com.cooper.homelab.core.presentation.editor.RefusalNotice] (a dialog with the server's reason, a snackbar with none), and a write
 * that could not reach the server is the inline save status, not an event here. What the page's own actions say beside the form is
 * [pageEvents]. The editor's extras are the page's own and are read from [state] by the page.
 */
@Composable
fun <T, X> SettingsEditorPage(
    title: String,
    state: EditorState<T, X>,
    events: Flow<EditorEvent>,
    actions: EditorActions<T>,
    pageEvents: Flow<PageEvent> = emptyFlow(),
    canSave: (T) -> Boolean = { true },
    scrolling: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(draft: T, enabled: Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notices = rememberRefusalNoticeState(snackbarHostState)
    RefusalNoticeHost(notices)
    PageEventEffect(pageEvents, snackbarHostState)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is EditorEvent.Refused -> notices.show(event.notice)
                // The inline save status says so, with its retry.
                EditorEvent.SaveFailed -> Unit
            }
        }
    }
    EditorFrame(
        title = title,
        state =
            when (state) {
                EditorState.Loading -> FrameState.Loading
                is EditorState.Failed -> FrameState.Error(state.failure.toSeerrError())
                is EditorState.Ready ->
                    FrameState.Ready(state.draft, state.saved, saveStatus = state.saveStatus.toUi(), saveFailed = state.saveFailed)
            },
        snackbarHostState = snackbarHostState,
        actions = actions,
        canSave = canSave,
        options = FrameOptions(showSaveAction = true, saveAsMade = true, footerCommit = false, commitLabel = null, scrolling = scrolling),
        bottomBar = bottomBar,
        extraActions = extraActions,
        content = content,
    )
}
