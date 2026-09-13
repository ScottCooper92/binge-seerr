package io.github.scottcooper92.binge.seerr.ui.requests

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
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import com.binge.designsystem.R as DesR

/** A list's first-load failure: the classified error with a retry, and the way back for a rejected session. */
@Composable
internal fun PagedRefreshError(
    error: Throwable,
    onRetry: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val classified = error.toSeerrError()
    ErrorScreen(
        error = classified,
        modifier = modifier,
        onRetry = if (classified == SeerrError.Unauthorized) onReconnect else onRetry,
    )
}

/** The footer under a list that is still showing: a spinner, or a tappable line to retry the next page. */
@Composable
internal fun PagedAppendState(
    state: LoadState,
    onRetry: () -> Unit,
    onReconnect: () -> Unit,
) {
    when (state) {
        is LoadState.Loading ->
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = dimensionResource(DesR.dimen.padding_m)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        is LoadState.Error -> {
            val rejected = state.error.toSeerrError() == SeerrError.Unauthorized
            Text(
                text = stringResource(if (rejected) R.string.requests_reconnect else R.string.requests_load_more_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = if (rejected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (rejected) onReconnect() else onRetry() }
                        .padding(vertical = dimensionResource(DesR.dimen.padding_m)),
            )
        }
        is LoadState.NotLoading -> Unit
    }
}
