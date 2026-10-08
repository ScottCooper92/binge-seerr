package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.previewReady

/**
 * The television hub's problem page, which Home shows in place of its requests while the server is not answering
 * (#796): loading, and each connection problem with its way out.
 */
class TvHubScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        TvHubBoard(state = HubUiState.Loading, actions = previewTvHubActions())
    }

    /** The server did not answer: Retry leads, with Disconnect beside it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Unreachable() {
        TvHubBoard(state = previewReady(health = ConnectionHealth.Unreachable), actions = previewTvHubActions())
    }

    /** A retry in flight: the problem stays, and the lead button says it is checking (#873). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun UnreachableRechecking() {
        TvHubBoard(state = previewReady(health = ConnectionHealth.Unreachable).copy(rechecking = true), actions = previewTvHubActions())
    }

    /** The local network is refused: the board's lead action is the permission, with Edit connection and Disconnect beside it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun LocalNetworkDenied() {
        TvHubBoard(state = previewReady(health = ConnectionHealth.LocalNetworkDenied), actions = previewTvHubActions())
    }

    /** A cold start the server never answered: no server to name, so the board keeps the app's title. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun UnreachableOnFirstRead() {
        TvHubBoard(state = HubUiState.Error(ConnectionHealth.Unreachable), actions = previewTvHubActions())
    }

    /** It answered, but the overview would not load. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun CouldNotLoad() {
        TvHubBoard(state = previewReady(health = ConnectionHealth.CouldNotLoad), actions = previewTvHubActions())
    }
}

private fun previewTvHubActions() = TvHubActions(onRetry = {}, onReconnect = {}, onDisconnect = {})
