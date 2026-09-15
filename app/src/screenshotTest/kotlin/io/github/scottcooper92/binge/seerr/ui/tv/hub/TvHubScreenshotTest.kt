package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.hub.previewAdminOverview
import io.github.scottcooper92.binge.seerr.ui.hub.previewLimitedOverview
import io.github.scottcooper92.binge.seerr.ui.hub.previewReady
import io.github.scottcooper92.binge.seerr.ui.hub.previewUnlimitedOverview

/**
 * The television hub, framed for the three accounts whose quota reads differently: an admin with one
 * metered type beside one unlimited, a user with movies spent, and a user metered on neither.
 */
class TvHubScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun AdminBoard() {
        TvHubBoard(state = previewReady(overview = previewAdminOverview()), actions = previewTvHubActions())
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun LimitedQuotaBoard() {
        TvHubBoard(state = previewReady(overview = previewLimitedOverview()), actions = previewTvHubActions())
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun UnlimitedQuotaBoard() {
        TvHubBoard(state = previewReady(overview = previewUnlimitedOverview()), actions = previewTvHubActions())
    }
}

private fun previewTvHubActions() =
    TvHubActions(
        onOpenRequests = {},
        onOpenIssues = {},
        onRetry = {},
        onReconnect = {},
        onDisconnect = {},
    )
