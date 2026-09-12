package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.InfoRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import com.binge.designsystem.R as DesR

/** Everything the setup screen can ask of its ViewModel, in one place so the entry stays a wiring. */
class SetupActions(
    val onEditAddress: (String) -> Unit,
    val onInspect: () -> Unit,
    val onChangeServer: () -> Unit,
    val onEditForm: (SignInForm.() -> SignInForm) -> Unit,
    val onConnect: () -> Unit,
    val onPlexLaunched: () -> Unit,
    val onCancelLink: () -> Unit,
    val onRequestPasswordReset: () -> Unit,
    val onDisconnect: () -> Unit,
)

/**
 * The one screen: the saved connection, or the two steps to make one. Built from the shared design
 * system's components and tokens, so it reads as part of Binge rather than a second app.
 */
@Composable
fun SetupScreen(
    state: SetupUiState,
    actions: SetupActions,
) {
    Scaffold(topBar = { BingeTopBar(title = stringResource(R.string.companion_name)) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                SetupUiState.Loading -> BingeLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                is SetupUiState.Address -> SetupAddressStep(state, actions.onEditAddress, actions.onInspect)
                is SetupUiState.SignIn -> {
                    SetupSignInStep(state, actions)
                    state.link?.let { link -> SetupLinkSheet(link, actions.onPlexLaunched, actions.onCancelLink) }
                }
                is SetupUiState.Connected -> ConnectedPanel(state.credentials, state.isDisconnecting, actions.onDisconnect)
            }
        }
    }
}

@Composable
private fun ConnectedPanel(
    credentials: SeerrCredentials,
    isDisconnecting: Boolean,
    onDisconnect: () -> Unit,
) {
    val spacing = dimensionResource(DesR.dimen.padding_m)
    Column(
        modifier = Modifier.fillMaxSize().padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(stringResource(R.string.connected_title, credentials.variant.displayName), style = MaterialTheme.typography.titleMedium)
        InfoRow(label = stringResource(R.string.connected_server_label), value = credentials.baseUrl)
        InfoRow(
            label = stringResource(R.string.connected_access_label),
            value =
                stringResource(
                    when (credentials.auth) {
                        is SeerrAuth.ApiKey -> R.string.connected_as_api_key
                        is SeerrAuth.Session -> R.string.connected_as_user
                    },
                ),
        )
        Text(stringResource(R.string.connected_hint), style = MaterialTheme.typography.bodyMedium)
        BingeOutlinedButton(
            label = stringResource(R.string.connected_disconnect),
            onClick = onDisconnect,
            enabled = !isDisconnecting,
            loading = isDisconnecting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun SetupError.messageRes(): Int =
    when (this) {
        SetupError.InvalidUrl -> R.string.setup_error_invalid_url
        SetupError.Rejected -> R.string.setup_error_rejected
        SetupError.Unreachable -> R.string.setup_error_unreachable
        SetupError.Unknown -> R.string.setup_error_unknown
        SetupError.LinkExpired -> R.string.setup_error_link_expired
    }
