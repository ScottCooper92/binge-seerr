package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.component.BingeBottomSheet
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen

/**
 * What a request's actions sheet shows until the request behind it has loaded: another request opened
 * from this page's list is fetched on open, so its sheet has a moment of loading, and a failure to retry.
 */
@Composable
internal fun RequestSheetPlaceholder(
    state: RequestDetailUiState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        RequestSheetPlaceholderContent(state = state, onRetry = onRetry)
    }
}

/** The sheet's body, apart from the sheet, so a frame can render it: a modal window does not capture. */
@Composable
internal fun RequestSheetPlaceholderContent(
    state: RequestDetailUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val held = modifier.fillMaxWidth().height(dimensionResource(R.dimen.request_sheet_placeholder_height))
    when (state) {
        is RequestDetailUiState.Error -> ErrorScreen(error = state.error, modifier = held, onRetry = onRetry)
        RequestDetailUiState.Loading, is RequestDetailUiState.Ready -> LoadingScreen(held)
    }
}
