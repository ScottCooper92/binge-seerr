package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.ModeChips
import io.github.scottcooper92.binge.seerr.ui.ModeFields
import io.github.scottcooper92.binge.seerr.ui.SetupServer
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.submitLabelRes
import com.binge.designsystem.R as DesR

/**
 * The sheet after the address is sent, when the code carried a key: where the TV is, and on its sign-in step the same
 * fields the phone's own sign-in has, whose answers go to the TV sealed for it alone.
 */
@Composable
internal fun SigningInContent(
    state: SendAddressUiState.SigningIn,
    actions: SendAddressActions,
) {
    when (val step = state.step) {
        SignInStep.Waiting -> {
            Body(stringResource(R.string.send_signin_waiting, state.tv))
            BingeLoadingIndicator()
            BingeOutlinedButton(
                label = stringResource(R.string.send_address_close),
                onClick = actions.onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is SignInStep.OnTv -> Done(stringResource(R.string.send_signin_on_tv, step.server), R.string.send_address_close, actions.onClose)
        is SignInStep.Form -> SignInFormContent(step, actions)
        is SignInStep.Session -> {
            Body(stringResource(R.string.send_signin_session, state.tv))
            BingeLoadingIndicator()
        }
        SignInStep.Connected -> Done(stringResource(R.string.send_signin_connected, state.tv), R.string.send_address_done, actions.onClose)
        SignInStep.Lost -> Done(stringResource(R.string.send_signin_lost), R.string.send_address_close, actions.onClose)
    }
}

@Composable
private fun SignInFormContent(
    step: SignInStep.Form,
    actions: SendAddressActions,
) {
    // The phone's own sign-in fields take a server and a state; this is neither, so a stand-in carries what they read.
    val server =
        SetupServer(step.server, step.server, SeerrVariant.Seerr, null, null, step.modes, canResetPassword = false, backdropUrl = null)
    val signIn =
        SetupUiState.SignIn(server = server, form = step.form, isConnecting = step.isSending, link = null, error = null, notice = null)
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Body(stringResource(R.string.send_signin_form, step.server))
        step.sessionOffer?.let { offer ->
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.AutoMirrored.Filled.Login,
                            label =
                                offer.userName?.let { stringResource(R.string.send_signin_continue_as, it) }
                                    ?: stringResource(R.string.send_signin_continue_as_you),
                            detail = stringResource(R.string.send_address_sign_in_shared),
                            loading = step.isSending && step.sendingSession,
                            disabled = step.isSending && !step.sendingSession,
                            onClick = actions.onSendSession,
                        ),
                    ),
            )
        }
        if (step.modes.size > 1) ModeChips(server, step.form.mode) { mode -> actions.onEditSignIn { copy(mode = mode) } }
        ModeFields(signIn, actions.onEditSignIn, onRequestPasswordReset = {}, onConnect = actions.onSendSignIn)
        Text(
            stringResource(R.string.send_signin_sealed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (step.rejected) {
            Text(
                stringResource(R.string.send_signin_rejected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        BingeFilledButton(
            label = stringResource(step.form.mode.submitLabelRes()),
            onClick = actions.onSendSignIn,
            enabled = step.canSend,
            loading = step.isSending,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Done(
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
