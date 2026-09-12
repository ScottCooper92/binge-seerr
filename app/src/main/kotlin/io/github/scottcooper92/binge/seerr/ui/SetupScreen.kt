package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeTopBar
import io.github.scottcooper92.binge.seerr.R

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
)

/**
 * The two steps to a connection. Built from the shared design system's components and tokens, so
 * it reads as part of Binge rather than a second app.
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
                // The home swaps to the hub on the credentials landing; this is the frame in between.
                is SetupUiState.Connected -> BingeLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

internal fun SetupError.messageRes(): Int =
    when (this) {
        SetupError.InvalidUrl -> R.string.setup_error_invalid_url
        SetupError.NotSeerr -> R.string.setup_error_not_seerr
        SetupError.Rejected -> R.string.setup_error_rejected
        SetupError.Unreachable -> R.string.setup_error_unreachable
        SetupError.Unknown -> R.string.setup_error_unknown
        SetupError.LinkExpired -> R.string.setup_error_link_expired
    }
