package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview

/**
 * The hub, which had no frame at all (#241). It is the app's landing screen, the list pane of the
 * wide-window layout, and the screen most likely to move under a design-system bump: it is built
 * almost entirely out of the submodule's cards, section headers and settings group.
 */
class HubScreenScreenshotTest {
    /** The canonical layout: an admin, every count the server offers, and the downloading strip. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun ready() = HubScreen(state = previewHub(), actions = previewHubActions)

    /** A plain requester sees one manage row and no quota — a different body, not a different layout. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun requester() = HubScreen(state = previewHub(overview = requesterOverview, downloading = emptyList()), actions = previewHubActions)

    /** Nothing downloading drops the strip and its header, which is the common steady state. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun idle() = HubScreen(state = previewHub(downloading = emptyList()), actions = previewHubActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = HubScreen(state = HubUiState.Loading, actions = previewHubActions)

    /** The server is gone: the plate offers a retry. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unreachable() = HubScreen(state = previewHub(health = ConnectionHealth.Unreachable), actions = previewHubActions)

    /** The session was rejected, which is the one problem a retry cannot mend — so it offers sign-in instead. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unauthorized() = HubScreen(state = previewHub(health = ConnectionHealth.Unauthorized), actions = previewHubActions)
}
