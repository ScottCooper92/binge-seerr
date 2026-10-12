package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageHosting
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.titleRes
import com.cooper.binge.companion.seerr.ui.state.toErrorKind

/**
 * A detail page that could not load, above the rail: what went wrong, and a retry. Shared by the request and issue pages.
 *
 * It reads as the phone's `ErrorScreen` does (#1058): the design system's [com.binge.designsystem.ErrorKind] picks the
 * glyph, and the title and message are this app's, since they name the server and the saved sign-in. It stays a
 * [TvMessagePage] rather than the design system's `TvErrorPlate`, which is a plate inside a board, not a page over one.
 */
@Composable
internal fun TvSeerrErrorPage(
    error: SeerrError,
    onRetry: () -> Unit,
) {
    TvMessagePage(
        headline = stringResource(error.titleRes()),
        body = stringResource(error.messageRes()),
        hosting = TvPageHosting.Overlay,
        icon = error.toErrorKind().icon,
        primary = ScreenAction(stringResource(R.string.hub_retry), onRetry),
    )
}
