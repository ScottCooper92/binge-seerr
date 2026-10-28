package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.DISABLED_ALPHA
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.submitLabelRes
import com.binge.designsystem.R as DesR

/** Whether the commit can be pressed: nothing is in flight, and the form has what the mode needs. */
private val SetupUiState.SignIn.canCommit: Boolean
    get() = !isConnecting && link == null && form.canSubmit

internal val CodeModes = setOf(SeerrSignInMode.QuickConnect, SeerrSignInMode.Plex)

/**
 * The sign-in step's buttons, centred under the fields: the commit, and beside it Forgot password? on a local account of
 * a server that can mail a reset link (#1037). That one asks for the link at once for the email typed, as the phone's
 * button does.
 */
@Composable
internal fun TvSignInActions(
    state: SetupUiState.SignIn,
    actions: SetupActions,
    initialFocus: TvSetupFocus?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        TvSignInCommit(state, actions, state.canCommit, initialFocus)
        if (state.form.mode == SeerrSignInMode.Local && state.server.canResetPassword) {
            val canReset = state.form.canRequestReset && !state.isConnecting && state.link == null && !state.resetCoolingDown
            TvButton(
                label = stringResource(R.string.setup_forgot_password),
                onClick = actions.onRequestPasswordReset,
                style = TvButtonStyle.Secondary,
                enabled = canReset,
                modifier = Modifier.alpha(if (canReset) 1f else DISABLED_ALPHA),
            )
        }
    }
}

/** The sign-in step's commit: Sign in, or Try again on a code tab after a failure. */
@Composable
private fun TvSignInCommit(
    state: SetupUiState.SignIn,
    actions: SetupActions,
    canCommit: Boolean,
    initialFocus: TvSetupFocus?,
) {
    val label =
        when {
            state.isConnecting -> R.string.tv_setup_connecting
            // The code tabs show this button only after a failure or a Back, when what it does is ask again.
            state.form.mode in CodeModes -> R.string.action_try_again
            else -> state.form.mode.submitLabelRes()
        }
    TvButton(
        label = stringResource(label),
        onClick = actions.onConnect,
        style = TvButtonStyle.Primary,
        enabled = canCommit,
        initiallyFocused = initialFocus == TvSetupFocus.Connect,
        // Dimmed as a whole, so it can't be read as the outlined button it replaces, and one width for every mode's label.
        modifier =
            Modifier
                .widthIn(min = dimensionResource(R.dimen.tv_form_commit_min_width))
                .alpha(if (canCommit) 1f else DISABLED_ALPHA),
    )
}
