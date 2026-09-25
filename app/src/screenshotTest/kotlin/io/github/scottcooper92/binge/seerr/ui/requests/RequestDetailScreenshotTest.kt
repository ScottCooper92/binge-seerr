package io.github.scottcooper92.binge.seerr.ui.requests

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
 * The request page on the shared detail shape: the hero under an overlay bar carrying Open and
 * Report, the overview collapsed with its toggle, the watch rows among the facts, and one primary
 * pinned in its own footer (#343) rather than scrolled with the rest of the content.
 *
 * The overview is seeded overflowing in every frame. Left alone the component only learns it
 * overflowed from `onTextLayout`, which fires after this lane has captured, so its collapsed-with-
 * toggle state — the only one a reader meets on a real synopsis — would never be in a baseline.
 */
class RequestDetailScreenshotTest {
    /** The Loading arm (#373): the hero, the headline's chips and overview, and the one unconditional facts row. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = RequestDetailSkeleton()

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

    /** Nothing left to do: no footer, and the scroll clears the safe area itself rather than a footer's. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun noPrimaryAction() = Frame(noPrimaryActionDetail())

    /**
     * Pending, in a pane narrower than the window (#343): the footer spans the pane it sits in rather
     * than the whole window it is measured against, exactly as [io.github.scottcooper92.binge.seerr.ui.hub.HubScreenshotTest.readyAsListPane]
     * proves for the hub. [LocalPaneWidth] is what `SeerrNavHost` would provide for a pane at that
     * width (#291) — without it the hero and the info rows would still take the window's 32dp.
     */
    @PreviewTest
    @SeerrListPanePreview
    @Composable
    fun asPane() =
        CompositionLocalProvider(LocalPaneWidth provides 360.dp) {
            Box(modifier = Modifier.width(360.dp)) {
                Frame(pendingDetail())
            }
        }

    /** Type, availability and 4K as three chips on the headline row, wrapping rather than clipping (#340). */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun partiallyAvailable4k() = Frame(partiallyAvailable4kDetail())

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
