package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview

/**
 * The hub, which had no committed baseline until now (#241). It is the app's most-touched screen —
 * the list pane of the wide-window layout, and the one screen assembled almost entirely from
 * design-system components — so a submodule bump that moves the settings row, the tag or the
 * progress meter lands here first.
 */
class HubScreenshotTest {
    /** The canonical layout: an administrator, every manage row, the downloading strip. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun ready() = HubScreen(state = previewReady(), actions = previewActions())

    /**
     * A user who may only request: one manage row, no quota, no downloads, and a stat the server
     * did not answer. Three arms the canonical frame cannot reach, at one cell.
     */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readyRestricted() =
        HubScreen(
            state = previewReady(overview = previewRestrictedOverview(), downloading = emptyList()),
            actions = previewActions(),
        )

    /**
     * The open section marked in the Manage group. It renders at the phone cell because that is the
     * cheapest render of the row's wash; which windows put a section beside the hub is the scene
     * strategy's business, not this composable's.
     */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readySectionOpen() = HubScreen(state = previewReady(), actions = previewActions(), selectedSection = HubSection.Requests)

    /** Server gone: the retry route out. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unreachable() = HubScreen(state = previewReady(health = ConnectionHealth.Unreachable), actions = previewActions())

    /** Reached but the dashboard never loaded — the same way out as [unreachable], a different glyph and copy. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun couldNotLoad() = HubScreen(state = previewReady(health = ConnectionHealth.CouldNotLoad), actions = previewActions())

    /** The session rejected: the one problem that offers signing in again rather than retrying. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unauthorized() = HubScreen(state = previewReady(health = ConnectionHealth.Unauthorized), actions = previewActions())

    /** Before the state resolves the bar has no server to name, so it falls back to the app's own. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = HubScreen(state = HubUiState.Loading, actions = previewActions())
}
