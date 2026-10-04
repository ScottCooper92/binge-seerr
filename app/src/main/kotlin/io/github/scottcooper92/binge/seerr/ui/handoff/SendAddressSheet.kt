package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/** The confirmation as a sheet; dismissing it is closing it, and nothing is sent. */
@Composable
internal fun SendAddressSheet(
    state: SendAddressUiState,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onClose) {
        SendAddressSheetContent(state = state, onSend = onSend, onClose = onClose)
    }
}

/**
 * What the sheet says for each state: the address and the TV it would go to, then the outcome.
 * The address is shown as it will be sent, in monospace, so what the user confirms is exactly that.
 */
@Composable
internal fun SendAddressSheetContent(
    state: SendAddressUiState,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.send_address_title), style = MaterialTheme.typography.titleLarge)
        when (state) {
            SendAddressUiState.Loading -> BingeLoadingIndicator()
            is SendAddressUiState.Ready -> ReadyContent(state, onSend, onClose)
            is SendAddressUiState.Sent -> Outcome(stringResource(R.string.send_address_sent, state.tv), R.string.send_address_done, onClose)
            SendAddressUiState.NotConnected ->
                Outcome(
                    stringResource(R.string.send_address_not_connected),
                    R.string.send_address_close,
                    onClose,
                )
            SendAddressUiState.Refused -> Outcome(stringResource(R.string.send_address_refused), R.string.send_address_close, onClose)
        }
    }
}

@Composable
private fun ReadyContent(
    state: SendAddressUiState.Ready,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    Body(stringResource(R.string.send_address_confirm, state.tv))
    Text(state.serverAddress, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.send_address_only_address),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (state.failed) {
        Text(
            stringResource(R.string.send_address_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
    BingeFilledButton(
        label = stringResource(if (state.isSending) R.string.send_address_sending else R.string.send_address_send),
        onClick = onSend,
        enabled = !state.isSending,
        loading = state.isSending,
        modifier = Modifier.fillMaxWidth(),
    )
    BingeOutlinedButton(label = stringResource(R.string.link_cancel), onClick = onClose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Outcome(
    message: String,
    closeLabel: Int,
    onClose: () -> Unit,
) {
    Body(message)
    BingeFilledButton(label = stringResource(closeLabel), onClick = onClose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}
