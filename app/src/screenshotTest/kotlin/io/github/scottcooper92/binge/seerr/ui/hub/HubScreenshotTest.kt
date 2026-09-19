package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalPaneWidth
import io.github.scottcooper92.binge.seerr.preview.SeerrListPanePreview
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

    /**
     * An administrator with nothing downloading, which is the steady state rather than an edge: it
     * is the only frame where the strip and its header are absent from an otherwise full hub.
     */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readyIdle() = HubScreen(state = previewReady(downloading = emptyList()), actions = previewActions())

    /**
     * The hub as the list pane: a wide window, but the hub itself only as wide as the pane it sits in.
     *
     * The other wide frames render it across the whole window, where `screen_content_inset`'s 32dp
     * looks right — so none of them could see #285, where that same 32dp was applied across a
     * phone-width pane. The width is pinned here rather than taken from a scaffold because the
     * scaffold is the scene's, not this composable's; [LocalPaneWidth] is what `SeerrNavHost` would
     * provide for a pane at that width (#291).
     */
    @PreviewTest
    @SeerrListPanePreview
    @Composable
    fun readyAsListPane() =
        CompositionLocalProvider(LocalPaneWidth provides 360.dp) {
            Box(modifier = Modifier.width(360.dp)) {
                HubScreen(
                    state = previewReady(),
                    actions = previewActions(),
                    selectedSection = HubSection.Requests,
                )
            }
        }

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
