package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.homelab.core.presentation.editor.EditorState
import com.cooper.homelab.core.uiandroid.editor.RefusalNoticeHost
import com.cooper.homelab.core.uiandroid.editor.rememberRefusalNoticeState
import kotlinx.coroutines.flow.Flow

/**
 * [EditorPage] for a page that saves as it changes on the shared library's [com.cooper.homelab.core.presentation.editor.SettingsEditor]:
 * it reads the editor's own [EditorState], and its events reach the page as [PageEvent]s, in one stream with everything else the page says. A refusal is the library's
 * [com.cooper.homelab.core.presentation.editor.RefusalNotice] (a dialog with the server's reason, a snackbar with none), and a write
 * that could not reach the server is the inline save status. The editor's extras are the page's own and are read from [state] by the page.
 */
@Composable
fun <T, X> SettingsEditorPage(
    title: String,
    state: EditorState<T, X>,
    events: Flow<PageEvent>,
    actions: EditorActions<T>,
    onPageEvent: (PageEvent) -> Boolean = { false },
    canSave: (T) -> Boolean = { true },
    scrolling: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(draft: T, enabled: Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notices = rememberRefusalNoticeState(snackbarHostState)
    RefusalNoticeHost(notices)
    PageEventEffect(events, snackbarHostState, notices, actions.onDeleted, actions.onCommitted, onPageEvent)
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
