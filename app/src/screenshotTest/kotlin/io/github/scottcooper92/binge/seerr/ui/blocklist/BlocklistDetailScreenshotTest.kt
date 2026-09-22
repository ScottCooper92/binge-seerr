package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview

/**
 * The blocked title's own page on the shared detail shape: the hero under an overlay bar carrying
 * Open, the facts (who blocked it and when), the tags, and Unblock pinned in its own footer — or,
 * for a viewer who may only view the list, none of the last three.
 */
class BlocklistDetailScreenshotTest {
    /** The layout, once: a manager's own page, with the footer and every fact filled in. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun manageable() = Frame(manageableBlocklistDetail())

    /** A viewer with no manage permission: no footer, and nothing to say who blocked it or when. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readOnly() = Frame(readOnlyBlocklistDetail())
}

@Composable
private fun Frame(state: BlocklistDetailUiState) {
    BlocklistDetailPage(state = state, onBack = {}, onPrimary = {})
}
