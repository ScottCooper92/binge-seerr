package io.github.scottcooper92.binge.seerr.ui.requests

import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.ui.state.MediaHeroDetailPlaceholder
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.requestDetail
import io.github.scottcooper92.binge.seerr.util.requestItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The request skeleton reserves what the resolved page fills (#572), swapped as [RequestDetailScreen]
 * swaps them. The page is in its common shape, with an action and a
 * sibling request, so a placeholder sized for the bare page fails. `HERO` is tagged by `MediaHeroDetailPage`,
 * on the design system's `DetailHero`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RequestDetailSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the skeleton holds the hero and the overview where the page puts them`() {
        val anchors = listOf(LayoutAnchors.Detail.HERO, LayoutAnchors.Detail.OVERVIEW).map(LayoutAnchors::section)
        rule.assertSkeletonReservesGeometry(anchors) { resolved ->
            SeerrTheme {
                if (resolved) {
                    RequestDetailPage(
                        detail = detail(),
                        onBack = {},
                        onOpen = null,
                        onReport = null,
                        onOpenRequest = {},
                        onOpenUser = {},
                    )
                } else {
                    MediaHeroDetailPlaceholder { RequestDetailSkeleton() }
                }
            }
        }
    }

    private fun detail(): RequestDetail =
        requestDetail(
            item = requestItem(id = 2),
            // The common shape: a viewer who can act on it.
            actions = RequestActions(canApprove = true, canDecline = true),
            overview = "A group of professional thieves and the detective who hunts them.",
            viewerId = null,
            // ...and a sibling request, the taller state a skeleton sized for the bare page would miss.
            siblings =
                listOf(
                    RequestSummary(
                        id = 3,
                        status = SeerrRequestStatusCode.Pending,
                        requestedBy = "ben",
                        requestedAtMillis = null,
                        is4k = true,
                        seasonNumbers = emptyList(),
                    ),
                ),
        )
}
