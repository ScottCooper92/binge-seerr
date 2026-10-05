package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.hub.BingeStatus
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.previewAdminOverview
import io.github.scottcooper92.binge.seerr.ui.hub.previewLimitedOverview
import io.github.scottcooper92.binge.seerr.ui.hub.previewReady
import io.github.scottcooper92.binge.seerr.ui.hub.previewUnlimitedOverview

/**
 * The television hub, framed for the three accounts whose quota reads differently: an admin with one
 * metered type beside one unlimited, a user with movies spent, and a user metered on neither. Beyond
 * those, the board's whole-screen arms (loading and the three connection problems) and a stat tile
 * with focus, which a remote can reach and which changes how the tile draws.
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

    /** The saved sign-in was rejected: Reconnect leads, since retrying cannot mend it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Unauthorized() {
        TvHubBoard(state = previewReady(health = ConnectionHealth.Unauthorized), actions = previewTvHubActions())
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun PendingTileFocused() {
        TvHubBoard(state = previewReady(), actions = previewTvHubActions(), initialFocusedTile = TILE_PENDING)
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun IssuesTileFocused() {
        TvHubBoard(state = previewReady(), actions = previewTvHubActions(), initialFocusedTile = TILE_ISSUES)
    }

    /** Binge is not installed: a card the remote can select, opening its Play Store listing. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun BingeNotInstalled() {
        TvHubBoard(state = previewReady(bingeStatus = BingeStatus.NotInstalled), actions = previewTvHubActions())
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun BingeNotInstalledFocused() {
        TvHubBoard(
            state = previewReady(bingeStatus = BingeStatus.NotInstalled),
            actions = previewTvHubActions(),
            initialBingeTileFocused = true,
        )
    }

    /** Installed but not yet allowed here: the hint that says where to allow it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun BingeNotConnected() {
        TvHubBoard(state = previewReady(bingeStatus = BingeStatus.NotConnected), actions = previewTvHubActions())
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun BingeConnected() {
        TvHubBoard(state = previewReady(bingeStatus = BingeStatus.Connected), actions = previewTvHubActions())
    }
}

private fun previewTvHubActions() =
    TvHubActions(
        onOpenRequests = {},
        onOpenIssues = {},
        onRetry = {},
        onReconnect = {},
        onDisconnect = {},
        onOpenBingeListing = {},
    )
