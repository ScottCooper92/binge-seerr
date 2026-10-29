package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeTextButton
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.flags.LocalFlags

/**
 * Disconnecting drops the saved server, so it asks first. The hub and Settings share it. On the demo server it is
 * the way out of the demo, and says that it clears what was done there.
 */
@Composable
internal fun DisconnectButton(
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    demo: Boolean = false,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val connectPromoted = LocalFlags.current.bingePromotion
    BingeTextButton(
        label = stringResource(if (demo) R.string.hub_demo_exit else R.string.hub_disconnect),
        onClick = { confirming = true },
        destructive = true,
        modifier = modifier.fillMaxWidth(),
    )
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(if (demo) R.string.hub_demo_exit_confirm_title else R.string.hub_disconnect_confirm_title),
            message =
                stringResource(
                    when {
                        demo -> R.string.hub_demo_exit_confirm_message
                        connectPromoted -> R.string.hub_disconnect_confirm_message
                        else -> R.string.hub_disconnect_confirm_message_plain
                    },
                ),
            confirmLabel = stringResource(if (demo) R.string.hub_demo_exit else R.string.hub_disconnect),
            destructive = true,
            onConfirm = {
                confirming = false
                onDisconnect()
            },
            onDismissRequest = { confirming = false },
        )
    }
}
