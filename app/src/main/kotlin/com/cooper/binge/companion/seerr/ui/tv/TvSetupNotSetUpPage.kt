package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvCompactButton
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.messageRes
import com.cooper.binge.companion.seerr.ui.notEncrypted
import com.binge.designsystem.R as DesR

/**
 * A server that has not finished its first-run setup, on a television: which server it is, the message in place of the
 * tabs, and a focusable Check again that reads it once more. Nothing here times out, so the message stays until the user
 * acts.
 */
@Composable
internal fun TvSetupNotSetUpPage(
    state: SetupUiState.NotSetUp,
    actions: SetupActions,
    modifier: Modifier = Modifier,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvSignInTabsPage(
        headline = state.server.title,
        modifier = modifier,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
                TvChangeServerButton(actions, leads = false, arrival = null)
            }
        },
        tabs = {},
    ) {
        NotSetUpBody(state, actions, Modifier.tvArrivalTarget(arrival))
    }
}

@Composable
private fun ColumnScope.NotSetUpBody(
    state: SetupUiState.NotSetUp,
    actions: SetupActions,
    checkModifier: Modifier,
) {
    TvFormNote(state.server.baseUrl, centered = true)
    if (state.server.notEncrypted) TvFormNote(stringResource(R.string.setup_not_encrypted), tone = TvFormNoteTone.Error, centered = true)
    TvFormNote(stringResource(R.string.setup_not_set_up_message), centered = true)
    state.error?.let { error -> TvFormNote(stringResource(error.messageRes()), tone = TvFormNoteTone.Error, centered = true) }
    TvCompactButton(
        label = stringResource(R.string.setup_check_again),
        onClick = actions.onCheckAgain,
        loading = state.isChecking,
        style = TvButtonStyle.Primary,
        modifier = checkModifier,
    )
}
