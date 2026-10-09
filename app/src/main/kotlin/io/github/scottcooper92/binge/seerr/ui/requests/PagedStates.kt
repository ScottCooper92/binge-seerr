package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import com.binge.designsystem.resolvedContentPadding
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import com.binge.designsystem.R as DesR

/**
 * A list's first-load failure: the classified error with a retry. A rejected session is retried like any other
 * failure; the root gate moves to sign-in once the server confirms the session is gone.
 */
@Composable
internal fun PagedRefreshError(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorScreen(error = error.toSeerrError(), modifier = modifier, onRetry = onRetry)
}

/** A refresh that failed behind rows still on screen: [messageRes] says the rows are stale, and a tap retries. */
@Composable
internal fun RefreshFailedLine(
    @StringRes messageRes: Int,
    onRetry: () -> Unit,
) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onRetry)
                .padding(resolvedContentPadding())
                .padding(vertical = dimensionResource(DesR.dimen.padding_s)),
    )
}

/** The footer under a list that is still showing: a spinner, or a tappable line to retry the next page. */
@Composable
internal fun PagedAppendState(
    state: LoadState,
    onRetry: () -> Unit,
) {
    when (state) {
        is LoadState.Loading ->
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = dimensionResource(DesR.dimen.padding_m)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        is LoadState.Error ->
            Text(
                text = stringResource(R.string.requests_load_more_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRetry)
                        .padding(vertical = dimensionResource(DesR.dimen.padding_m)),
            )
        is LoadState.NotLoading -> Unit
    }
}
