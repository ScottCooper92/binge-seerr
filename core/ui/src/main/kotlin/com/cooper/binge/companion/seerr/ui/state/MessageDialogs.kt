package com.cooper.binge.companion.seerr.ui.state

import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.binge.designsystem.component.BingeMessageDialog
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.cooper.binge.companion.seerr.seerr.SeerrError

/** A message the user must read to act: what happened, and the cause and next step. A snackbar would cut it off or take it away. */
data class DialogMessage(
    val title: String,
    val body: String,
)

/** Shows [message] as a dialog the user acknowledges; nothing while it is null. */
@Composable
fun MessageDialogHost(
    message: DialogMessage?,
    onDismiss: () -> Unit,
) {
    message?.let { BingeMessageDialog(title = it.title, message = it.body, onDismiss = onDismiss) }
}

/** Whether this failure explains a cause and a next step, so it is a dialog rather than a snackbar. */
fun SeerrError.needsDialog(): Boolean = this == SeerrError.Unauthorized || this == SeerrError.LocalNetworkDenied

/** This failure as a dialog message; call it only where [needsDialog]. */
fun SeerrError.dialogMessage(resources: Resources): DialogMessage =
    DialogMessage(title = resources.getString(titleRes()), body = resources.getString(messageRes()))

/**
 * Says a failure the right way: a failure that explains a cause and a next step ([needsDialog]) as a dialog with one OK, any
 * other as a snackbar of its usual message. Made by [rememberErrorReporter], which also draws the dialog.
 */
@Stable
class ErrorReporter(
    private val snackbarHostState: SnackbarHostState,
    private val resources: Resources,
    private val dialog: MutableState<DialogMessage?>,
) {
    suspend fun report(error: SeerrError) {
        snackbarHostState.currentSnackbarData?.dismiss()
        if (error.needsDialog()) {
            dialog.value = error.dialogMessage(resources)
        } else {
            snackbarHostState.showSnackbar(resources.getString(error.messageRes()), SnackbarMessageKind.Error)
        }
    }
}

/** An [ErrorReporter] over [snackbarHostState], with the dialog it raises drawn here. */
@Composable
fun rememberErrorReporter(snackbarHostState: SnackbarHostState): ErrorReporter {
    val resources = LocalResources.current
    val dialog = remember { mutableStateOf<DialogMessage?>(null) }
    MessageDialogHost(dialog.value) { dialog.value = null }
    return remember(snackbarHostState, resources) { ErrorReporter(snackbarHostState, resources, dialog) }
}
