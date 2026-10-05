package io.github.scottcooper92.binge.seerr.ui.requests

import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.ui.state.MediaHeroDetailPlaceholder
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
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
        RequestDetail(
            item =
                RequestItem(
                    id = 2,
                    tmdbId = 2,
                    mediaType = RequestMediaType.Movie,
                    title = "Heat",
                    posterUrl = null,
                    year = "1995",
                    requestedBy = "ana",
                    requestedById = 3,
                    requestedAtMillis = null,
                    status = SeerrRequestStatusCode.Approved,
                    mediaStatus = null,
                    download = null,
                    seasonNumbers = emptyList(),
                    is4k = false,
                ),
            // The common shape: a viewer who can act on it.
            actions = RequestActions(canApprove = true, canDecline = true),
            canEdit = false,
            canEditDestination = false,
            backdropUrl = null,
            overview = "A group of professional thieves and the detective who hunts them.",
            modifiedBy = null,
            modifiedById = null,
            viewerId = null,
            canManageUsers = false,
            updatedAtMillis = null,
            seasons = emptyList(),
            destination = null,
            downloads = emptyList(),
            mediaId = 9,
            canReportIssue = false,
            webUrl = "https://seerr.example/movie/2",
            mediaServerUrl = null,
            serviceUrl = null,
            media = null,
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
