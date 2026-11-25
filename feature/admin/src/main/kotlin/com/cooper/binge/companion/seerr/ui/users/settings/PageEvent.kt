package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.cooper.binge.companion.seerr.feature.admin.R
import com.cooper.binge.companion.seerr.ui.state.DialogMessage
import com.cooper.binge.companion.seerr.ui.state.MessageDialogHost
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * What a page's own actions beside the form tell the screen once: the library's editor events say what happened to a save, and
 * these say what happened to everything else on the page. A page sends them through [PageEvents], so one waits for a collector
 * and is delivered once.
 */
sealed interface PageEvent {
    /** The record is gone from the server, so the page that edited it has nothing left to show. */
    data object Deleted : PageEvent

    /** An action beside the form succeeded; [messageRes] says which. */
    data class Notice(
        @StringRes val messageRes: Int,
    ) : PageEvent

    /** A test the server could not carry out, and why: a dialog, since the reason is the server's and has no length bound. */
    data class TestFailed(
        val reason: String,
    ) : PageEvent

    /**
     * A change on a page that saves as it changes, which the snackbar offers to take back (#940): [messageRes] formatted
     * with [argRes]'s text, and [undo] to call when the user asks.
     */
    class Undoable(
        @StringRes val messageRes: Int,
        @StringRes val argRes: Int,
        val undo: () -> Unit,
    ) : PageEvent
}

/** A page's channel of [PageEvent]s: one collector, and an event waits for it if there is none yet, so a rotation does not lose it. */
class PageEvents {
    private val channel = Channel<PageEvent>(Channel.BUFFERED)

    val flow: Flow<PageEvent> = channel.receiveAsFlow()

    fun send(event: PageEvent) {
        channel.trySend(event)
    }
}

/** Each [PageEvent] as a snackbar, or a dialog for a test's reason; a newer one supersedes the one still showing. */
@Composable
internal fun PageEventEffect(
    events: Flow<PageEvent>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    var dialog by remember { mutableStateOf<DialogMessage?>(null) }
    MessageDialogHost(dialog) { dialog = null }
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                is PageEvent.TestFailed ->
                    dialog =
                        DialogMessage(
                            resources.getString(R.string.server_settings_agent_test_failed_title),
                            resources.getString(R.string.server_settings_agent_test_failed_body, event.reason),
                        )
                is PageEvent.Notice ->
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes), SnackbarMessageKind.Confirmation)
                is PageEvent.Undoable -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            resources.getString(event.messageRes, resources.getString(event.argRes)),
                            SnackbarMessageKind.Confirmation,
                            actionLabel = resources.getString(R.string.user_settings_undo),
                        )
                    if (result == SnackbarResult.ActionPerformed) event.undo()
                }
                // The page is leaving on this one; a snackbar on a screen being popped is not seen.
                PageEvent.Deleted -> Unit
            }
        }
    }
}
