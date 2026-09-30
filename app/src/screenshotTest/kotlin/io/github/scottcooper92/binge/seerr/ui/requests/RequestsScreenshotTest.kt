package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import kotlinx.coroutines.flow.emptyFlow

/**
 * The requests browser's arm for a signed-in user it could not read: the error with its retry, in place
 * of the skeleton that used to hang. The listed arm needs a paging stream, so its rows are framed by
 * [RequestListScreenshotTest] instead.
 */
class RequestsScreenshotTest {
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scopeUnreachable() = Frame(SeerrError.Unreachable)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scopeUnauthorized() = Frame(SeerrError.Unauthorized)
}

@Composable
private fun Frame(error: SeerrError) {
    RequestsScreen(
        state = RequestsUiState.Error(error),
        requestsFor = { emptyFlow() },
        events = emptyFlow(),
        shouldRefresh = { _, _ -> false },
        actions =
            RequestsActions(
                onBack = {},
                onFilterChange = {},
                onSortChange = {},
                onOpen = {},
                onOpenActions = {},
                onDismissActions = {},
                onRetryLoad = {},
                onApprove = {},
                onRetry = {},
                onDecline = { _, _ -> },
                onRemove = { _, _ -> },
            ),
    )
}
