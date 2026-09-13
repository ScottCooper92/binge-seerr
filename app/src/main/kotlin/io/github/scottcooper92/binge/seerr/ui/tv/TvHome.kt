package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvMessagePlate
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestUiState
import io.github.scottcooper92.binge.seerr.ui.SetupViewModel
import io.github.scottcooper92.binge.seerr.ui.actions

/**
 * The television shell of the app's own UI: setup while no server is saved, and once one is, the plate
 * that says so. The hub, the lists and their moderation arrive on TV with a later phase; until then Binge's
 * rail is where a television requests titles, and this app is the thing it requests through.
 */
@Composable
internal fun TvSeerrShell(viewModel: TvHomeViewModel = hiltViewModel()) {
    BingeTvTheme {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        when (val current = state) {
            TvHomeUiState.Loading -> TvLoadingPlate()
            TvHomeUiState.Setup -> TvSetupEntry()
            is TvHomeUiState.Connected -> TvConnectedScreen(serverUrl = current.serverUrl, onDisconnect = viewModel::disconnect)
        }
    }
}

@Composable
private fun TvSetupEntry(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSetupScreen(state = state, actions = viewModel.actions())
}

/** The hand-off's television shell: the same state and callbacks as the phone's picker, under the TV theme. */
@Composable
internal fun TvAdvancedRequestShell(
    state: AdvancedRequestUiState,
    actions: TvAdvancedRequestActions,
) {
    BingeTvTheme {
        TvAdvancedRequestScreen(state = state, actions = actions)
    }
}

/**
 * The connected plate: which server, and the one action a television has for it. Disconnect lands focus
 * so the remote has somewhere to be, though nothing here is urgent.
 */
@Composable
internal fun TvConnectedScreen(
    serverUrl: String,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyFocused: Boolean = false,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvMessagePlate(
        headline = stringResource(R.string.tv_home_connected_headline),
        body = stringResource(R.string.tv_home_connected_body, serverUrl),
        icon = Icons.Filled.CheckCircle,
        alignment = Alignment.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        TvButton(
            label = stringResource(R.string.tv_home_disconnect),
            onClick = onDisconnect,
            style = TvButtonStyle.Destructive,
            initiallyFocused = initiallyFocused,
            modifier = Modifier.tvArrivalTarget(arrival),
        )
    }
}
