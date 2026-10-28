package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvCheckboxRow
import com.binge.designsystem.tv.component.TvTabRow
import com.binge.designsystem.tv.component.TvTabUi
import com.binge.designsystem.tv.component.TvTextField
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.messageRes
import com.cooper.binge.companion.seerr.ui.rememberAllowLocalNetwork
import com.binge.designsystem.tv.R as TvR

/**
 * The server step on a television: the same tabs as the sign-in's, with the code first and the address typed with the
 * remote as the fallback for a network where the phone can't reach the TV. [onScan] is null when no code is on offer,
 * which leaves the typed address alone on the page; [onType] leaves the code for the fallback.
 */
@Composable
internal fun TvSetupAddressPage(
    state: SetupUiState.Address,
    actions: SetupActions,
    modifier: Modifier,
    initialFocus: TvSetupFocus?,
    scanSelected: Boolean,
    onScan: (() -> Unit)?,
    onType: () -> Unit,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    InspectionEndFocusEffect(state.isInspecting, arrival)
    // The demo is a third tab for a viewer with no server to point at; it has no state of the setup's own.
    var demoSelected by rememberSaveable { mutableStateOf(false) }
    val tabsOffered = onScan != null || state.canTryDemo
    TvSignInTabsPage(
        headline = stringResource(R.string.tv_setup_code_headline),
        modifier = modifier,
        tabs = {
            if (tabsOffered) {
                TvTabRow(
                    tabs =
                        listOfNotNull(
                            onScan?.let { TvTabUi(key = SCAN_KEY, label = stringResource(R.string.tv_setup_send_from_phone)) },
                            TvTabUi(key = ADDRESS_KEY, label = stringResource(R.string.tv_setup_type_address)),
                            if (state.canTryDemo) TvTabUi(key = DEMO_KEY, label = stringResource(R.string.setup_try_demo)) else null,
                        ),
                    selectedKey =
                        when {
                            demoSelected -> DEMO_KEY
                            scanSelected -> SCAN_KEY
                            else -> ADDRESS_KEY
                        },
                    onSelect = { key ->
                        demoSelected = key == DEMO_KEY
                        when (key) {
                            SCAN_KEY -> onScan?.invoke()
                            ADDRESS_KEY -> onType()
                            else -> actions.onCancelHandOff()
                        }
                    },
                    arrival = arrival.takeIf { scanSelected && !demoSelected },
                )
            }
        },
    ) {
        when {
            demoSelected -> TvDemoContent(actions, enabled = !state.isInspecting)
            scanSelected -> TvAddressCodeContent(state, actions)
            else ->
                // The sign-in fields' width, so the two steps' columns line up.
                Column(
                    modifier = Modifier.width(dimensionResource(R.dimen.tv_form_field_width)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(TvR.dimen.tv_two_pane_action_gap)),
                ) { TvSetupAddressFields(state, actions, initialFocus, arrival) }
        }
    }
}

private const val SCAN_KEY = "scan"
private const val ADDRESS_KEY = "address"
private const val DEMO_KEY = "demo"

/**
 * Puts the remote back where the page lands once an inspection ends on this page, as a failure does (#1044). The field
 * can't hold focus while it is disabled, and a disabled button that picks focus up keeps it when it is enabled again.
 */
@Composable
private fun InspectionEndFocusEffect(
    isInspecting: Boolean,
    arrival: TvArrivalFocus,
) {
    var inspected by remember { mutableStateOf(false) }
    LaunchedEffect(isInspecting) {
        if (isInspecting) {
            inspected = true
        } else if (inspected) {
            inspected = false
            restoreTvOverlayFocus(arrival.requester)
        }
    }
}

@Composable
private fun ColumnScope.TvSetupAddressFields(
    state: SetupUiState.Address,
    actions: SetupActions,
    initialFocus: TvSetupFocus?,
    arrival: TvArrivalFocus,
) {
    // While the address waits on the local-network permission, asking for it is the way on: Continue could only fail (#1099).
    val allow = if (state.needsLocalNetwork) rememberAllowLocalNetwork(actions.onLocalNetworkChanged) else null
    // An address a phone sent waits for the user to go on, so the page lands on the way on, not the keyboard (#1084).
    val wayOn = if (state.awaitingConfirm && !state.awaitingCleartextConsent) Modifier.tvArrivalTarget(arrival) else Modifier
    TvTextField(
        value = state.serverUrl,
        onValueChange = actions.onEditAddress,
        label = stringResource(R.string.setup_server_url),
        enabled = !state.isInspecting,
        keyboardType = KeyboardType.Uri,
        fillWidth = true,
        placeholder = stringResource(R.string.placeholder_server_url),
        initiallyFocused = initialFocus == TvSetupFocus.Address,
        // Where the page lands: the remote came here to type, and the keyboard waits for select. An address a phone sent
        // lands on the way on instead, or on the opt-in when it waits on that (#907, #1084).
        arrival = arrival.takeUnless { state.awaitingConfirm || state.awaitingCleartextConsent },
        onImeAction = {
            if (allow != null) {
                allow.run()
            } else if (state.canContinue) {
                actions.onInspect()
            }
        },
    )
    // The way on sits centred under the field it acts on, as a button with a word on it.
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (allow != null) {
            TvButton(
                label = stringResource(allow.shortLabel),
                onClick = allow.run,
                style = TvButtonStyle.Primary,
                initiallyFocused = initialFocus == TvSetupFocus.Continue,
                modifier = wayOn,
            )
        } else {
            TvContinueButton(state, actions, initialFocus, wayOn)
        }
    }
    TvSetupAddressNotes(state, actions, arrival)
}

/** Continue, under the field. */
@Composable
private fun TvContinueButton(
    state: SetupUiState.Address,
    actions: SetupActions,
    initialFocus: TvSetupFocus?,
    modifier: Modifier,
) {
    TvButton(
        label = stringResource(if (state.isInspecting) R.string.tv_setup_checking else R.string.setup_continue),
        onClick = actions.onInspect,
        style = TvButtonStyle.Primary,
        enabled = state.canContinue,
        initiallyFocused = initialFocus == TvSetupFocus.Continue,
        // Disabled, it is not a stop: the remote skips it until there is an address to continue with.
        modifier = modifier.focusProperties { canFocus = state.canContinue },
    )
}

@Composable
private fun TvSetupAddressNotes(
    state: SetupUiState.Address,
    actions: SetupActions,
    arrival: TvArrivalFocus,
) {
    // What appears under the field takes no height in the pane, so the field stays where it is as a note comes and goes.
    Column(
        modifier = Modifier.fillMaxWidth().belowWithoutHeight(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(TvR.dimen.tv_two_pane_action_gap)),
    ) {
        // A plain-HTTP address has the opt-in to answer instead, and ticking it is the user going on (#907).
        if (state.awaitingConfirm && !state.insecure) TvFormNote(stringResource(R.string.tv_setup_confirm_sent_address))
        if (state.insecure) {
            // A checkbox, not an action: Continue beside the field is what goes on once it is ticked (#915). It sits right
            // under the field it qualifies, with the reason below it. An address a phone sent lands here, and ticking it goes
            // on by itself, since the phone is already waiting (#907).
            TvCheckboxRow(
                label = stringResource(R.string.setup_allow_cleartext),
                checked = state.cleartextAllowed,
                onCheckedChange = actions.onAllowCleartext,
                enabled = !state.isInspecting,
                modifier = if (state.awaitingCleartextConsent) Modifier.tvArrivalTarget(arrival) else Modifier,
            )
            TvFormNote(stringResource(R.string.setup_insecure_warning), tone = TvFormNoteTone.Error)
        }
        state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error) }
        // Said before the system prompt, so a permission dialog is never the first the user hears of it.
        if (state.needsLocalNetwork) TvFormNote(stringResource(R.string.setup_local_network_explanation))
    }
}

/** Measures and draws the content as usual, but reports no height, so a centred column doesn't recentre around it. */
private fun Modifier.belowWithoutHeight(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        layout(placeable.width, 0) { placeable.place(0, 0) }
    }
