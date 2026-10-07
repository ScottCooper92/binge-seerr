package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.handoff.HandOffSignInModes
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupNotice
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.messageRes

/** Lines the status under the code always takes, so a longer message never moves what is above it. */
private const val STATUS_LINES = 2

/**
 * Setup on a television as one screen: a code for the phone, and a line under it saying where things are. The server
 * address and the sign-in both happen on the phone, in the Seerr app or the page the code opens; the TV only shows the
 * code and follows along. The same page serves both steps, so the TV doesn't change screens under the user while they
 * look at their phone.
 *
 * Typing with the remote is the fallback, for a network where the phone can't reach the TV: [onManual] opens the forms.
 */
@Composable
internal fun TvSetupCodePage(
    state: SetupUiState,
    actions: SetupActions,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val address = state as? SetupUiState.Address
    val signIn = state as? SetupUiState.SignIn
    // The address step's code ends when the app goes to the background; bring one back when the page is shown again.
    val needsCode = address != null && address.code == null && address.handOff !is AddressHandOff.Unavailable && !address.isInspecting
    LaunchedEffect(needsCode) { if (needsCode) actions.onStartHandOff() }
    // The sign-in step keeps the address's code, or opens one if the address was typed.
    LaunchedEffect(signIn != null) { if (signIn != null) actions.onOfferSignInCode() }
    // Leaving the app stops the address step's listener; coming back brings a fresh code (above).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (address != null) actions.onCancelHandOff() }
    val unavailable = address?.handOff as? AddressHandOff.Unavailable
    val code = address?.code ?: signIn?.code
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvFormPage(
        // Put up by the app itself when the server rejected the session (#810): it says so, then the same code.
        headline =
            stringResource(
                if (signIn?.notice == SetupNotice.SessionRejected) R.string.hub_unauthorized_headline else R.string.tv_setup_code_headline,
            ),
        body = stringResource(R.string.tv_setup_code_body),
        icon = Icons.Filled.Tv,
        modifier = modifier,
        actionScrolls = false,
        buttonBar = true,
        // Under the code, which is centred in its pane.
        pinnedCentered = true,
        copyAction = {
            TvButton(
                label = stringResource(R.string.tv_setup_enter_manually),
                onClick = onManual,
                // The way out when the phone can't reach the TV, so it leads only when there's no code to scan.
                style = if (unavailable != null) TvButtonStyle.Primary else TvButtonStyle.Secondary,
                modifier = Modifier.tvArrivalTarget(arrival),
            )
        },
        pinnedAction =
            signIn?.let {
                {
                    TvButton(label = stringResource(R.string.setup_change_server), onClick = actions.onChangeServer)
                    actions.onDisconnect?.let { TvButton(label = stringResource(R.string.hub_disconnect), onClick = it) }
                }
            },
    ) {
        when {
            unavailable != null -> TvHandOffContent(unavailable)
            code != null -> TvHandOffContent(code)
            else -> Box(Modifier.weight(1f).fillMaxWidth())
        }
        val (status, isError) = codeStatus(address, signIn, hasCode = code != null)
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = STATUS_LINES,
            maxLines = STATUS_LINES,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What the line under the code says, and whether it is a problem. */
@Composable
private fun codeStatus(
    address: SetupUiState.Address?,
    signIn: SetupUiState.SignIn?,
    hasCode: Boolean,
): Pair<String, Boolean> =
    when {
        signIn != null -> {
            val server = signIn.server.title
            when {
                signIn.isConnecting -> stringResource(R.string.tv_setup_status_signing_in, server) to false
                signIn.error != null -> stringResource(signIn.error.messageRes()) to true
                signIn.server.modes.none { it in HandOffSignInModes } ->
                    stringResource(R.string.tv_setup_status_found_on_tv, server) to false
                else -> stringResource(R.string.tv_setup_status_found, server) to false
            }
        }
        address == null -> "" to false
        address.handOff is AddressHandOff.Unavailable -> "" to false
        address.isInspecting -> stringResource(R.string.tv_setup_status_checking) to false
        address.error != null -> stringResource(address.error.messageRes()) to true
        !hasCode -> stringResource(R.string.tv_setup_status_preparing) to false
        else -> stringResource(R.string.tv_setup_status_waiting) to false
    }
