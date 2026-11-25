package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.cooper.binge.companion.seerr.feature.admin.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.state.DialogMessage
import com.cooper.binge.companion.seerr.ui.state.MessageDialogHost
import com.cooper.binge.companion.seerr.ui.state.dialogMessage
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.needsDialog
import com.cooper.homelab.core.presentation.editor.CommitEvent
import com.cooper.homelab.core.presentation.editor.CommitState
import com.cooper.homelab.core.uiandroid.editor.RefusalNoticeHost
import com.cooper.homelab.core.uiandroid.editor.rememberRefusalNoticeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow

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
    events: Flow<CommitEvent>,
    actions: EditorActions<T>,
    pageEvents: Flow<PageEvent> = emptyFlow(),
    onPageEvent: (PageEvent) -> Boolean = { false },
    onCommitEvent: (CommitEvent) -> Boolean = { false },
    canSave: (T) -> Boolean = { true },
    footerCommit: Boolean = false,
    commitLabel: String? = null,
    scrolling: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(draft: T, enabled: Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CommitEventEffect(events, snackbarHostState, actions.onCommitted, onCommitEvent)
    PageEventEffect(pageEvents, snackbarHostState, actions.onDeleted, onPageEvent)
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

/** One collector of the form's events: what each says, once, on the page's snackbar or in a dialog. */
@Composable
private fun CommitEventEffect(
    events: Flow<CommitEvent>,
    snackbarHostState: SnackbarHostState,
    onCommitted: () -> Unit,
    onCommitEvent: (CommitEvent) -> Boolean,
) {
    val currentOnCommitted by rememberUpdatedState(onCommitted)
    val currentOnCommitEvent by rememberUpdatedState(onCommitEvent)
    val resources = LocalResources.current
    val notices = rememberRefusalNoticeState(snackbarHostState)
    RefusalNoticeHost(notices)
    var dialog by remember { mutableStateOf<DialogMessage?>(null) }
    MessageDialogHost(dialog) { dialog = null }
    LaunchedEffect(events) {
        events.collectLatest { event ->
            if (currentOnCommitEvent(event)) return@collectLatest
            when (event) {
                CommitEvent.Committed -> {
                    currentOnCommitted()
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(R.string.user_settings_saved), SnackbarMessageKind.Confirmation)
                }
                is CommitEvent.Refused -> notices.show(event.notice)
                is CommitEvent.Failed -> {
                    val error: SeerrError = event.failure.toSeerrError()
                    snackbarHostState.currentSnackbarData?.dismiss()
                    if (error.needsDialog()) {
                        dialog = error.dialogMessage(resources)
                    } else {
                        snackbarHostState.showSnackbar(resources.getString(error.messageRes()), SnackbarMessageKind.Error)
                    }
                }
            }
        }
    }
}
