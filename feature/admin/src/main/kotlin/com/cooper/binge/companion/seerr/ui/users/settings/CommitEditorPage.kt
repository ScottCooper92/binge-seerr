package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.homelab.core.presentation.editor.CommitEvent
import com.cooper.homelab.core.presentation.editor.CommitState
import com.cooper.homelab.core.uiandroid.editor.RefusalNoticeHost
import com.cooper.homelab.core.uiandroid.editor.rememberRefusalNoticeState
import kotlinx.coroutines.flow.Flow

/**
 * [EditorPage] for a page on the shared library's [com.cooper.homelab.core.presentation.editor.CommitForm]: it reads the form's own
 * [CommitState] and [CommitEvent]s, so the page needs no adapter between them. A commit that goes through says Saved; a refusal is
 * the library's [com.cooper.homelab.core.presentation.editor.RefusalNotice] (a dialog with the server's reason, a snackbar with
 * none); any other failure is a snackbar, or a dialog for the two the user must read to act (a rejected session, a blocked local
 * network). The form's extras are the page's own and are read from [state] by the page.
 */
@Composable
fun <T, X> CommitEditorPage(
    title: String,
    state: CommitState<T, X>,
    events: Flow<PageEvent>,
    actions: EditorActions<T>,
    onPageEvent: (PageEvent) -> Boolean = { false },
    canSave: (T) -> Boolean = { true },
    footerCommit: Boolean = false,
    commitLabel: String? = null,
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
                CommitState.Loading -> FrameState.Loading
                is CommitState.Failed -> FrameState.Error(state.failure.toSeerrError())
                is CommitState.Ready -> FrameState.Ready(state.draft, state.saved, saving = state.committing)
            },
        snackbarHostState = snackbarHostState,
        actions = actions,
        canSave = canSave,
        options = FrameOptions(showSaveAction = true, saveAsMade = false, footerCommit, commitLabel, scrolling),
        bottomBar = bottomBar,
        extraActions = extraActions,
        content = content,
    )
}
