package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview

/**
 * The request page on the shared detail shape: the hero under an overlay bar carrying Open and
 * Report, the overview collapsed with its toggle, the watch rows among the facts, and one primary.
 *
 * The overview is seeded overflowing in every frame. Left alone the component only learns it
 * overflowed from `onTextLayout`, which fires after this lane has captured, so its collapsed-with-
 * toggle state — the only one a reader meets on a real synopsis — would never be in a baseline.
 */
class RequestDetailScreenshotTest {
    /** The layout, once: pending, so the primary reads as a review. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun pending() = Frame(pendingDetail())

    /** The same layout settled, which is the primary's other label and the media half of its sheet. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun settled() = Frame(settledDetail())

    /** A title with a second request against it: the "Also requested" section, and the clear-data note. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withSiblings() = Frame(detailWithSiblings())

    /**
     * #342: the title chip and the season chip disagree — the headline caption and the season
     * row's own shape are what make that read as coherent rather than contradictory.
     */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun partiallyAvailablePendingSeason() = Frame(partiallyAvailablePendingSeasonDetail())
}

@Composable
private fun Frame(detail: RequestDetail) {
    RequestDetailPage(
        detail = detail,
        onBack = {},
        onOpen = {},
        onReport = {},
        onPrimary = {},
        onOpenSibling = {},
        onOpenUser = {},
        initiallyOverflowing = true,
    )
}
