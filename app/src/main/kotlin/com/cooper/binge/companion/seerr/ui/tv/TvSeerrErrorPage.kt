package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.tv.template.TvErrorPage
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageHosting
import com.binge.designsystem.tv.template.currentTvPageHosting
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.rememberAllowLocalNetwork
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.titleRes
import com.cooper.binge.companion.seerr.ui.state.toErrorKind

/**
 * A detail page that could not load, above the rail: what went wrong, and a retry. Shared by the request and issue pages.
 *
 * It is the design system's [TvErrorPage] with this app's words (#1058): the [com.binge.designsystem.ErrorKind] picks the
 * glyph, and the title and message are this app's, since they name the server and the saved sign-in.
 */
@Composable
internal fun TvSeerrErrorPage(
    error: SeerrError,
    onRetry: () -> Unit,
) {
    if (error == SeerrError.LocalNetworkDenied) {
        TvLocalNetworkDeniedPage(onRetry = onRetry, hosting = TvPageHosting.Overlay)
        return
    }
    TvErrorPage(
        kind = error.toErrorKind(),
        onRetry = onRetry,
        hosting = TvPageHosting.Overlay,
        title = stringResource(error.titleRes()),
        message = stringResource(error.messageRes()),
    )
}

/**
 * The hub's answer to a server Android will not let this app reach (#1507), as a page: the same words, with Allow where
 * Try again would sit, since only the permission fixes it. Coming back from the prompt or from Settings retries.
 */
@Composable
internal fun TvLocalNetworkDeniedPage(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    hosting: TvPageHosting = currentTvPageHosting(),
) {
    val allow = rememberAllowLocalNetwork(onRetry)
    TvMessagePage(
        body = stringResource(SeerrError.LocalNetworkDenied.messageRes()),
        modifier = modifier,
        hosting = hosting,
        headline = stringResource(SeerrError.LocalNetworkDenied.titleRes()),
        icon = Icons.Filled.CloudOff,
        primary = ScreenAction(stringResource(allow.shortLabel), allow.run),
        announce = true,
    )
}
