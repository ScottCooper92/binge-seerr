package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.handoff.HandOffSignInModes
import com.cooper.binge.companion.seerr.ui.AddressHandOff
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.messageRes

/** Lines the status under the code always takes, so a longer message never moves what is above it. */
private const val STATUS_LINES = 2

/**
 * The server step's code tab: the code for the phone, and a line under it saying where things are. The server
 * address and the sign-in both happen on the phone, in the Seerr app or the page the code opens; the TV only shows the
 * code and follows along.
 */
@Composable
internal fun ColumnScope.TvAddressCodeContent(
    state: SetupUiState.Address,
    actions: SetupActions,
) {
    // The code ends when the app goes to the background; bring one back when the page is shown again.
    val needsCode = state.code == null && state.handOff !is AddressHandOff.Unavailable && !state.isInspecting
    LaunchedEffect(needsCode) { if (needsCode) actions.onStartHandOff() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { actions.onCancelHandOff() }
    val unavailable = state.handOff as? AddressHandOff.Unavailable
    TvHandOffContent(handOff = unavailable ?: state.code, preparing = needsCode)
    val (status, isError) = codeStatus(state, null, hasCode = state.code != null)
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
        // The card says it is getting a code ready, in the code's place.
        !hasCode -> "" to false
        else -> stringResource(R.string.tv_setup_status_waiting) to false
    }

/** The sign-in step's code tab: the code for the phone and the line under it, with no page around them. */
@Composable
internal fun ColumnScope.TvSignInCodeContent(
    state: SetupUiState.SignIn,
    actions: SetupActions,
) {
    // The sign-in step keeps the address's code, or opens one if the address was typed.
    LaunchedEffect(Unit) { actions.onOfferSignInCode() }
    TvHandOffContent(handOff = state.code, preparing = false)
    val (status, isError) = codeStatus(address = null, signIn = state, hasCode = state.code != null)
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
