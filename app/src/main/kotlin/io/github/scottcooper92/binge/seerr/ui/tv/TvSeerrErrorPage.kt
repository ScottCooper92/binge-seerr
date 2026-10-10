package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageAction
import com.binge.designsystem.tv.template.TvPageHosting
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.state.messageRes

/** A detail page that could not load, above the rail: what went wrong, and a retry. Shared by the request and issue pages. */
@Composable
internal fun TvSeerrErrorPage(
    error: SeerrError,
    onRetry: () -> Unit,
) {
    TvMessagePage(
        body = stringResource(error.messageRes()),
        hosting = TvPageHosting.Overlay,
        icon = Icons.Filled.Warning,
        primary = TvPageAction(stringResource(R.string.hub_retry), onRetry),
    )
}
