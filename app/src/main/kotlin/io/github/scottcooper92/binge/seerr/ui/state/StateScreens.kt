package io.github.scottcooper92.binge.seerr.ui.state

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.binge.designsystem.ErrorKind
import com.binge.designsystem.template.LoadingMessageScreen
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import com.binge.designsystem.R as DesR
import com.binge.designsystem.template.EmptyScreen as DesignEmptyScreen
import com.binge.designsystem.template.ErrorScreen as DesignErrorScreen

/**
 * The three whole-screen states every screen renders around its content, drawn by the design system's
 * message screens so they read as Binge's do. What is this app's own is the mapping from a [SeerrError]
 * to the design system's [ErrorKind], and the copy of the three failures that kind does not cover.
 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    LoadingMessageScreen(modifier)
}

/** An empty state: nothing here yet, said once. */
@Composable
fun EmptyScreen(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(DesR.string.empty_screen_title),
    icon: ImageVector = Icons.Filled.SearchOff,
) {
    DesignEmptyScreen(message = message, modifier = modifier, title = title, icon = icon)
}

/** The failure classified once in `SeerrErrors.kt`, with a retry where the caller offers one. */
@Composable
fun ErrorScreen(
    error: SeerrError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    DesignErrorScreen(
        kind = error.toErrorKind(),
        onRetry = onRetry,
        modifier = modifier,
        title = stringResource(error.titleRes()),
        message = stringResource(error.messageRes()),
    )
}

/**
 * The design system's kind for a failure, which picks the icon and the default copy. The three failures that are this
 * app's own say so through [titleRes] and [messageRes]: nothing connected, a spent request quota, and a refused request.
 */
internal fun SeerrError.toErrorKind(): ErrorKind =
    when (this) {
        SeerrError.Unauthorized -> ErrorKind.Auth
        SeerrError.Forbidden -> ErrorKind.Forbidden
        SeerrError.Quota -> ErrorKind.RateLimited
        SeerrError.NotFound -> ErrorKind.NotFound
        SeerrError.Unreachable -> ErrorKind.Network
        SeerrError.Server -> ErrorKind.Server
        SeerrError.NotConnected, SeerrError.Rejected, SeerrError.Unknown -> ErrorKind.Generic
    }

@StringRes
internal fun SeerrError.titleRes(): Int =
    when (this) {
        SeerrError.NotConnected -> R.string.state_error_not_connected_title
        SeerrError.Quota -> R.string.state_error_quota_title
        SeerrError.Rejected -> R.string.state_error_rejected_title
        else -> toErrorKind().titleRes
    }

@StringRes
internal fun SeerrError.messageRes(): Int =
    when (this) {
        SeerrError.NotConnected -> R.string.state_error_not_connected_message
        SeerrError.Quota -> R.string.state_error_quota_message
        SeerrError.Rejected -> R.string.state_error_rejected_message
        else -> toErrorKind().messageRes
    }

@Preview(showBackground = true)
@Composable
private fun PreviewErrorScreen() {
    SeerrTheme { ErrorScreen(error = SeerrError.Unreachable, onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewEmptyScreen() {
    SeerrTheme { EmptyScreen(message = "No requests yet.") }
}
