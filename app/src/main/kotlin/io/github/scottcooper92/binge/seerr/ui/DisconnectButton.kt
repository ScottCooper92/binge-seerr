package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R

/** Disconnecting drops the saved server, so it asks first. The hub and Settings share it. */
@Composable
internal fun DisconnectButton(
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    BingeOutlinedButton(
        label = stringResource(R.string.hub_disconnect),
        onClick = { confirming = true },
        modifier = modifier.fillMaxWidth(),
    )
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(R.string.hub_disconnect_confirm_title),
            message = stringResource(R.string.hub_disconnect_confirm_message),
            confirmLabel = stringResource(R.string.hub_disconnect),
            destructive = true,
            onConfirm = {
                confirming = false
                onDisconnect()
            },
            onDismiss = { confirming = false },
        )
    }
}
