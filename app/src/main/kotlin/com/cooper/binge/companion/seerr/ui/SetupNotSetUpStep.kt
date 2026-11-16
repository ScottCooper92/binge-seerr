package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeTextButton
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * Step two for a server that has not finished its first-run setup: which server it is, and that there is nothing to sign in
 * to yet. No field is offered, because no credential could work.
 */
@Composable
internal fun SetupNotSetUpContent(state: SetupUiState.NotSetUp) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(state.server.baseUrl, style = MaterialTheme.typography.bodyLarge)
        if (state.server.notEncrypted) {
            Text(
                stringResource(R.string.setup_not_encrypted),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(stringResource(R.string.setup_not_set_up_message), style = MaterialTheme.typography.bodyMedium)
        state.error?.let { error ->
            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The one commit is reading the server again; Change server is the way off it. */
@Composable
internal fun SetupNotSetUpFooter(
    state: SetupUiState.NotSetUp,
    actions: SetupActions,
) {
    BingeFilledButton(
        label = stringResource(R.string.setup_check_again),
        onClick = actions.onCheckAgain,
        enabled = !state.isChecking,
        loading = state.isChecking,
        modifier = Modifier.fillMaxWidth(),
    )
    BingeTextButton(
        label = stringResource(R.string.setup_change_server),
        onClick = actions.onChangeServer,
        enabled = !state.isChecking,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Step two's content: the sign-in for a server that is set up, the message for one that is not. */
@Composable
internal fun SetupSecondStepContent(
    step: SetupUiState?,
    actions: SetupActions,
) {
    when (step) {
        is SetupUiState.NotSetUp -> SetupNotSetUpContent(step)
        is SetupUiState.SignIn -> SetupSignInContent(step, actions)
        else -> Unit
    }
}

/** Step two's footer, chosen the same way as [SetupSecondStepContent]. */
@Composable
internal fun SetupSecondStepFooter(
    step: SetupUiState?,
    actions: SetupActions,
) {
    when (step) {
        is SetupUiState.NotSetUp -> SetupNotSetUpFooter(step, actions)
        is SetupUiState.SignIn -> SetupSignInFooter(step, actions)
        else -> Unit
    }
}
