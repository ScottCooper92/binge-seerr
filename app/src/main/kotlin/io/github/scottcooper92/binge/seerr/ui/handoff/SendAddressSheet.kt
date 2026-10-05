package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.binge.designsystem.component.BingeTextButton
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/** The confirmation as a sheet; dismissing it is closing it, and nothing is sent. */
@Composable
internal fun SendAddressSheet(
    state: SendAddressUiState,
    actions: SendAddressActions,
) {
    BingeBottomSheet(onDismissRequest = actions.onClose) {
        SendAddressSheetContent(state = state, actions = actions)
    }
}

/** What the sheet can ask for. Only [onSend] sends anything; choosing and typing change the choice. */
internal data class SendAddressActions(
    val onChoose: (AddressChoice) -> Unit = {},
    val onEditOther: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * What the sheet says for each state: the address, or the addresses to choose from, and the TV it
 * would go to, then the outcome. An address is shown as it will be sent, in monospace, so what the
 * user confirms is exactly that.
 */
@Composable
internal fun SendAddressSheetContent(
    state: SendAddressUiState,
    actions: SendAddressActions,
) {
    val onClose = actions.onClose
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.send_address_title), style = MaterialTheme.typography.titleLarge)
        when (state) {
            SendAddressUiState.Loading -> BingeLoadingIndicator()
            is SendAddressUiState.Ready -> ReadyContent(state, actions)
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
    actions: SendAddressActions,
) {
    if (state.isSingle) {
        SingleAddress(state, actions)
    } else {
        Body(stringResource(R.string.send_address_choose, state.tv))
        AddressChoices(state, actions.onChoose, actions.onEditOther)
    }
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
        onClick = actions.onSend,
        enabled = state.canSend,
        loading = state.isSending,
        modifier = Modifier.fillMaxWidth(),
    )
    BingeOutlinedButton(label = stringResource(R.string.link_cancel), onClick = actions.onClose, modifier = Modifier.fillMaxWidth())
}

/** The one address there is, read-only: no list to choose from, and a way to type another. */
@Composable
private fun SingleAddress(
    state: SendAddressUiState.Ready,
    actions: SendAddressActions,
) {
    val candidate = state.candidates.single()
    Body(stringResource(R.string.send_address_confirm, state.tv))
    Text(candidate.address, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
    if (candidate.mayBeUnreachable) NotLocalNote(centred = true)
    BingeTextButton(
        label = stringResource(R.string.send_address_other),
        onClick = { actions.onChoose(AddressChoice.Other) },
        enabled = !state.isSending,
    )
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
