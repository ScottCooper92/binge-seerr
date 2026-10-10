package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.template.MessageScreen
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.AllowLocalNetwork
import io.github.scottcooper92.binge.seerr.ui.DisconnectButton

/**
 * Server gone or the dashboard not loaded: retry, or edit the connection. A session the server rejected never reaches
 * here, since the app goes to sign-in instead (#810). [rechecking] is a retry in flight, which the primary button shows.
 */
@Composable
internal fun ConnectionProblem(
    health: ConnectionHealth,
    allow: AllowLocalNetwork,
    onRetry: () -> Unit,
    rechecking: Boolean,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Three ways out, one destructive, so the design system's stacked actions rather than its two-button row (#777).
    MessageScreen(
        modifier = modifier,
        headline =
            stringResource(
                when (health) {
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_headline
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_headline
                    else -> R.string.hub_unreachable_headline
                },
            ),
        body =
            stringResource(
                when (health) {
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_body
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_body
                    else -> R.string.hub_unreachable_body
                },
            ),
        icon =
            when (health) {
                ConnectionHealth.CouldNotLoad -> Icons.Filled.HourglassEmpty
                else -> Icons.Filled.CloudOff
            },
        actions = {
            if (health == ConnectionHealth.LocalNetworkDenied) {
                BingeFilledButton(label = stringResource(allow.label), onClick = allow.run, modifier = Modifier.fillMaxWidth())
            } else {
                // While a retry is in flight it says so, and can't be pressed again; the other ways out stay open.
                BingeFilledButton(
                    label = stringResource(if (rechecking) R.string.hub_rechecking else R.string.hub_retry),
                    onClick = onRetry,
                    loading = rechecking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BingeOutlinedButton(
                label = stringResource(R.string.settings_edit_connection),
                onClick = onReconnect,
                modifier = Modifier.fillMaxWidth(),
            )
            DisconnectButton(onDisconnect)
        },
    )
}
