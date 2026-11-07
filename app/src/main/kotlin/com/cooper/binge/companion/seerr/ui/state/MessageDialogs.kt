package com.cooper.binge.companion.seerr.ui.state

import android.content.res.Resources
import androidx.compose.runtime.Composable
import com.binge.designsystem.component.BingeMessageDialog
import com.cooper.binge.companion.seerr.seerr.SeerrError

/** A message the user must read to act: what happened, and the cause and next step. A snackbar would cut it off or take it away. */
internal data class DialogMessage(
    val title: String,
    val body: String,
)

/** Shows [message] as a dialog the user acknowledges; nothing while it is null. */
@Composable
internal fun MessageDialogHost(
    message: DialogMessage?,
    onDismiss: () -> Unit,
) {
    message?.let { BingeMessageDialog(title = it.title, message = it.body, onDismiss = onDismiss) }
}

/** Whether this failure explains a cause and a next step, so it is a dialog rather than a snackbar. */
internal fun SeerrError.needsDialog(): Boolean = this == SeerrError.Unauthorized || this == SeerrError.LocalNetworkDenied

/** This failure as a dialog message; call it only where [needsDialog]. */
internal fun SeerrError.dialogMessage(resources: Resources): DialogMessage =
    DialogMessage(title = resources.getString(titleRes()), body = resources.getString(messageRes()))
