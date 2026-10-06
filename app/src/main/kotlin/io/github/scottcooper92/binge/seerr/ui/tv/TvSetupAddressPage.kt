package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvIconButton
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.TvOverlayArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.messageRes
import io.github.scottcooper92.binge.seerr.ui.rememberAllowLocalNetwork
import com.binge.designsystem.R as DesR

/**
 * The address step: one page whose left pane stays put while the right one swaps between the code for a phone
 * and the typed form, so choosing between them changes only what there is to do.
 */
@Composable
internal fun TvSetupAddressPage(
    state: SetupUiState.Address,
    actions: SetupActions,
    modifier: Modifier,
    initialFocus: TvSetupFocus?,
    awaitingCode: Boolean,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    val handOff = state.handOff
    val showingCode = handOff != null || awaitingCode
    // The target moves from the field to the button, or back, as the pane swaps; offer focus again when it does.
    TvOverlayArrivalFocusEffect(arrival.requester, key = showingCode to (handOff != null))
    TvFormPage(
        headline = stringResource(R.string.tv_setup_headline),
        body = stringResource(if (showingCode) R.string.tv_handoff_body else R.string.tv_setup_address_body),
        icon = Icons.Filled.Dns,
        modifier = modifier,
        actionScrolls = !showingCode,
        note = if (showingCode) stringResource(R.string.tv_handoff_note) else null,
        // The switch between the two panes is in the same place on both, and is the only thing pinned.
        pinnedAction = {
            if (showingCode) {
                TvHandOffTypeInstead(onClick = actions.onCancelHandOff, arrival = arrival, enabled = handOff != null)
            } else {
                TvButton(
                    label = stringResource(R.string.tv_setup_send_from_phone),
                    onClick = actions.onStartHandOff,
                    enabled = !state.isInspecting,
                    modifier = Modifier.tvArrivalTarget(arrival),
                )
            }
        },
    ) {
        when {
            handOff != null -> {
                TvHandOffLifecycle(actions.onCancelHandOff)
                TvHandOffContent(handOff)
            }
            awaitingCode -> Unit
            else -> TvSetupAddressFields(state, actions, initialFocus)
        }
    }
}

@Composable
private fun ColumnScope.TvSetupAddressFields(
    state: SetupUiState.Address,
    actions: SetupActions,
    initialFocus: TvSetupFocus?,
) {
    // The way on sits beside the field, as tall as it, so the address and what to do with it read as one row.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.Bottom,
    ) {
        TvTextField(
            value = state.serverUrl,
            onValueChange = actions.onEditAddress,
            label = stringResource(R.string.setup_server_url),
            enabled = !state.isInspecting,
            keyboardType = KeyboardType.Uri,
            fillWidth = true,
            modifier = Modifier.weight(1f),
            placeholder = stringResource(R.string.placeholder_server_url),
            initiallyFocused = initialFocus == TvSetupFocus.Address,
        )
        TvIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = stringResource(if (state.isInspecting) R.string.tv_setup_checking else R.string.setup_continue),
            onClick = actions.onInspect,
            style = TvButtonStyle.Primary,
            enabled = state.canContinue,
            initiallyFocused = initialFocus == TvSetupFocus.Continue,
            modifier =
                Modifier
                    .height(
                        dimensionResource(R.dimen.tv_form_field_height),
                    ).widthIn(min = dimensionResource(R.dimen.tv_form_field_height)),
        )
    }
    if (state.insecure) {
        TvFormNote(stringResource(R.string.setup_insecure_warning), tone = TvFormNoteTone.Error)
        TvOptionRow(
            label = stringResource(R.string.setup_allow_cleartext),
            selected = state.cleartextAllowed,
            onSelect = { actions.onAllowCleartext(!state.cleartextAllowed) },
            modifier = Modifier.width(dimensionResource(R.dimen.tv_form_field_width)),
        )
    }
    state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error) }
    if (state.needsLocalNetwork) {
        val allow = rememberAllowLocalNetwork(actions.onLocalNetworkChanged)
        TvFormNote(stringResource(R.string.setup_local_network_explanation))
        TvButton(label = stringResource(allow.label), onClick = allow.run, style = TvButtonStyle.Secondary)
    }
}
