package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.template.TvErrorPage
import com.binge.designsystem.tv.template.TvPageHosting
import com.cooper.binge.companion.seerr.seerr.SeerrError
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
    TvErrorPage(
        kind = error.toErrorKind(),
        onRetry = onRetry,
        hosting = TvPageHosting.Overlay,
        title = stringResource(error.titleRes()),
        message = stringResource(error.messageRes()),
    )
}
