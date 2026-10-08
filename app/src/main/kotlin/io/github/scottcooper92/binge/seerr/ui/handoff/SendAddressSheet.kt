package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingePinField
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.SignInForm
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

/** What the sheet can ask for. Only [onSend] sends anything; editing changes the field, and the tick only what Send includes. */
internal data class SendAddressActions(
    val onEnterPin: (String) -> Unit = {},
    val onEdit: (String) -> Unit = {},
    val onChooseSignIn: (Boolean) -> Unit = {},
    val onSend: () -> Unit = {},
    val onEditSignIn: ((SignInForm.() -> SignInForm) -> Unit) = {},
    val onSendSignIn: () -> Unit = {},
    val onSendSession: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * What the sheet says for each state: the address in an editable field, and the TV it would go to,
 * then the outcome. Send sends what the field holds, normalised, and nothing else.
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
        // The PIN's field holds focus until its last digit; when it goes, focus would fall to the sheet's drag handle,
        // the first thing left that takes it, and a keyboard user would see the handle highlighted (#914). It goes to
        // Send instead, which is what the next key should press.
        val send = remember { FocusRequester() }
        var pinned by remember { mutableStateOf(false) }
        LaunchedEffect(state is SendAddressUiState.EnterPin, state is SendAddressUiState.Ready) {
            if (state is SendAddressUiState.EnterPin) pinned = true
            if (state is SendAddressUiState.Ready && pinned) {
                pinned = false
                // A Send that can't be pressed can't take focus either; the field is then where the user goes next anyway.
                if (state.canSend) send.requestFocus()
            }
        }
        when (state) {
            SendAddressUiState.Loading -> BingeLoadingIndicator()
            is SendAddressUiState.EnterPin -> EnterPinContent(state, actions)
            is SendAddressUiState.Ready -> ReadyContent(state, actions, send)
            is SendAddressUiState.SigningIn -> SigningInContent(state, actions)
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
    send: FocusRequester,
) {
    SendAddressField(state, actions.onEdit, actions.onSend)
    state.signIn?.let { offer ->
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.AutoMirrored.Filled.Login,
                        label =
                            offer.userName?.let { stringResource(R.string.send_address_sign_in_as, it) }
                                ?: stringResource(R.string.send_address_sign_in_as_you),
                        detail = stringResource(R.string.send_address_sign_in_shared),
                        toggled = state.signInChosen,
                        disabled = state.isSending,
                        onClick = { actions.onChooseSignIn(!state.signInChosen) },
                    ),
                ),
        )
    }
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
        modifier = Modifier.fillMaxWidth().focusRequester(send),
    )
}

/** The TV's PIN, typed before the sheet does anything with the code. */
@Composable
private fun EnterPinContent(
    state: SendAddressUiState.EnterPin,
    actions: SendAddressActions,
) {
    Body(stringResource(R.string.send_pin_body, state.tv))
    val wrong = stringResource(R.string.send_pin_wrong)
    BingePinField(
        value = state.entered,
        onValueChange = actions.onEnterPin,
        label = stringResource(R.string.send_pin_label),
        error = wrong.takeIf { state.wrong },
    )
    if (state.wrong) {
        Text(wrong, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
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
