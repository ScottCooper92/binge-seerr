package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import com.binge.designsystem.R as DesR

/** The three whole-screen states every ported screen renders around its content. */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BingeLoadingIndicator()
    }
}

@Composable
fun EmptyScreen(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.state_empty_title),
    icon: ImageVector = Icons.Filled.SearchOff,
    action: @Composable (() -> Unit)? = null,
) {
    StateLayout(icon = icon, title = title, message = message, modifier = modifier, action = action)
}

/** The failure classified once in `SeerrErrors.kt`, with a retry where the caller offers one. */
@Composable
fun ErrorScreen(
    error: SeerrError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    StateLayout(
        icon = error.icon(),
        title = stringResource(error.titleRes()),
        message = stringResource(error.messageRes()),
        // Merged so the announcement is the whole message, not just the title; the retry button
        // keeps its own merge boundary, so it stays an independent focus stop.
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        action =
            onRetry?.let { retry ->
                {
                    BingeFilledButton(
                        label = stringResource(R.string.action_try_again),
                        leadingIcon = Icons.Filled.Refresh,
                        onClick = retry,
                    )
                }
            },
    )
}

@Composable
private fun StateLayout(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(dimensionResource(DesR.dimen.state_icon_container_size))
                    .clip(BingeShapes.Pill)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(dimensionResource(DesR.dimen.placeholder_icon_size)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_s)))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
            action()
        }
    }
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

private fun SeerrError.titleRes(): Int =
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

private fun SeerrError.messageRes(): Int =
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
    BingeExpressiveTheme { ErrorScreen(error = SeerrError.Unreachable, onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewEmptyScreen() {
    BingeExpressiveTheme { EmptyScreen(message = "No requests yet.") }
}
