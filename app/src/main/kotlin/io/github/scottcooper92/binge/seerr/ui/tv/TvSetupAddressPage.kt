package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvIconButton
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.messageRes
import io.github.scottcooper92.binge.seerr.ui.rememberAllowLocalNetwork
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * The address typed with the remote: the fallback from the code page ([TvSetupCodePage]) for a network where the
 * phone can't reach the TV. Its left pane is the code page's, so stepping between them moves nothing there.
 */
@Composable
internal fun TvSetupAddressPage(
    state: SetupUiState.Address,
    actions: SetupActions,
    modifier: Modifier,
    initialFocus: TvSetupFocus?,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvFormPage(
        headline = stringResource(R.string.tv_setup_headline),
        // The code page's body, so leaving it for this form changes nothing on the left.
        body = stringResource(R.string.tv_handoff_body),
        icon = Icons.Filled.Dns,
        modifier = modifier,
        buttonBar = true,
        // Back to the code, in the same place the way here was.
        copyAction = {
            TvButton(
                label = stringResource(R.string.tv_setup_send_from_phone),
                onClick = actions.onStartHandOff,
                enabled = !state.isInspecting,
            )
        },
    ) {
        TvSetupAddressFields(state, actions, initialFocus, arrival)
    }
}

@Composable
private fun ColumnScope.TvSetupAddressFields(
    state: SetupUiState.Address,
    actions: SetupActions,
    initialFocus: TvSetupFocus?,
    arrival: TvArrivalFocus,
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
            // Where the page lands: the remote came here to type, and the keyboard waits for select. An address a phone sent
            // that waits on the opt-in lands on the opt-in instead (#907).
            arrival = arrival.takeUnless { state.awaitingCleartextConsent },
            onDone = { if (state.canContinue) actions.onInspect() },
        )
        TvIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = stringResource(if (state.isInspecting) R.string.tv_setup_checking else R.string.setup_continue),
            onClick = actions.onInspect,
            style = TvButtonStyle.Primary,
            enabled = state.canContinue,
            initiallyFocused = initialFocus == TvSetupFocus.Continue,
            // Disabled, it is not a stop: the remote skips it until there is an address to continue with.
            modifier =
                Modifier
                    .focusProperties { canFocus = state.canContinue }
                    .height(
                        dimensionResource(R.dimen.tv_form_field_height),
                    ).widthIn(min = dimensionResource(R.dimen.tv_form_field_height)),
        )
    }
    // What appears under the field takes no height in the pane, so the field stays where it is as a note comes and goes.
    Column(
        modifier = Modifier.fillMaxWidth().belowWithoutHeight(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(TvR.dimen.tv_two_pane_action_gap)),
    ) {
        if (state.insecure) {
            // A checkbox, not an action: Continue beside the field is what goes on once it is ticked (#915). It sits right
            // under the field it qualifies, with the reason below it. An address a phone sent lands here, and ticking it goes
            // on by itself, since the phone is already waiting (#907).
            TvCheckboxRow(
                label = stringResource(R.string.setup_allow_cleartext),
                checked = state.cleartextAllowed,
                onCheckedChange = actions.onAllowCleartext,
                modifier = if (state.awaitingCleartextConsent) Modifier.tvArrivalTarget(arrival) else Modifier,
            )
            TvFormNote(stringResource(R.string.setup_insecure_warning), tone = TvFormNoteTone.Error)
        }
        state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error) }
        if (state.needsLocalNetwork) {
            val allow = rememberAllowLocalNetwork(actions.onLocalNetworkChanged)
            TvFormNote(stringResource(R.string.setup_local_network_explanation))
            TvButton(label = stringResource(allow.label), onClick = allow.run, style = TvButtonStyle.Secondary)
        }
    }
}

/** Measures and draws the content as usual, but reports no height, so a centred column doesn't recentre around it. */
private fun Modifier.belowWithoutHeight(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        layout(placeable.width, 0) { placeable.place(0, 0) }
    }
