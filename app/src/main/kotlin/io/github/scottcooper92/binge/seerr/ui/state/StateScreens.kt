package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.binge.designsystem.template.LoadingMessageScreen
import com.binge.designsystem.template.MessageScreen
import com.binge.designsystem.template.ScreenAction
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme

/**
 * The three whole-screen states every screen renders around its content, drawn by the design system's
 * [MessageScreen] so they read as Binge's do. What is this app's own is the copy, and the mapping from
 * a [SeerrError] to it.
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
    title: String = stringResource(R.string.state_empty_title),
    icon: ImageVector = Icons.Filled.SearchOff,
) {
    MessageScreen(body = message, headline = title, icon = icon, modifier = modifier)
}

/** The failure classified once in `SeerrErrors.kt`, with a retry where the caller offers one. */
@Composable
fun ErrorScreen(
    error: SeerrError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    MessageScreen(
        body = stringResource(error.messageRes()),
        headline = stringResource(error.titleRes()),
        icon = error.icon(),
        primary = onRetry?.let { ScreenAction(stringResource(R.string.action_try_again), it, Icons.Filled.Refresh) },
        // The whole message is announced, not just the headline: it replaced what the user was reading.
        announce = true,
        modifier = modifier,
    )
}

private fun SeerrError.icon(): ImageVector =
    when (this) {
        SeerrError.NotConnected -> Icons.Filled.PowerOff
        SeerrError.Unauthorized -> Icons.Filled.Lock
        SeerrError.Forbidden -> Icons.Filled.Block
        SeerrError.Quota -> Icons.Filled.HourglassEmpty
        SeerrError.NotFound -> Icons.Filled.SearchOff
        SeerrError.Unreachable -> Icons.Filled.WifiOff
        SeerrError.Server -> Icons.Filled.CloudOff
        SeerrError.Rejected, SeerrError.Unknown -> Icons.Filled.ErrorOutline
    }

internal fun SeerrError.titleRes(): Int =
    when (this) {
        SeerrError.NotConnected -> R.string.state_error_not_connected_title
        SeerrError.Unauthorized -> R.string.state_error_unauthorized_title
        SeerrError.Forbidden -> R.string.state_error_forbidden_title
        SeerrError.Quota -> R.string.state_error_quota_title
        SeerrError.NotFound -> R.string.state_error_not_found_title
        SeerrError.Unreachable -> R.string.state_error_unreachable_title
        SeerrError.Server -> R.string.state_error_server_title
        SeerrError.Rejected -> R.string.state_error_rejected_title
        SeerrError.Unknown -> R.string.state_error_unknown_title
    }

internal fun SeerrError.messageRes(): Int =
    when (this) {
        SeerrError.NotConnected -> R.string.state_error_not_connected_message
        SeerrError.Unauthorized -> R.string.state_error_unauthorized_message
        SeerrError.Forbidden -> R.string.state_error_forbidden_message
        SeerrError.Quota -> R.string.state_error_quota_message
        SeerrError.NotFound -> R.string.state_error_not_found_message
        SeerrError.Unreachable -> R.string.state_error_unreachable_message
        SeerrError.Server -> R.string.state_error_server_message
        SeerrError.Rejected -> R.string.state_error_rejected_message
        SeerrError.Unknown -> R.string.state_error_unknown_message
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
