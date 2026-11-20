package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.DetailState
import com.binge.designsystem.template.ScreenAction
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.rememberAllowLocalNetwork
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.titleRes
import com.cooper.binge.companion.seerr.ui.state.toErrorKind

/**
 * The template's failed state for a title page's [error], with this app's headline and message. A server Android will not let this
 * app reach is fixed by the permission and not by trying again, so there the page offers Allow in place of Try again, and retries once
 * the permission is answered.
 */
@Composable
internal fun titleFailedState(
    error: SeerrError,
    onRetry: () -> Unit,
): DetailState.Failed {
    val allow = if (error == SeerrError.LocalNetworkDenied) rememberAllowLocalNetwork(onRetry) else null
    return DetailState.Failed(
        message = stringResource(error.messageRes()),
        title = stringResource(error.titleRes()),
        retryable = allow == null,
        kind = error.toErrorKind(),
        secondary = allow?.let { ScreenAction(stringResource(it.label), it.run) },
    )
}
