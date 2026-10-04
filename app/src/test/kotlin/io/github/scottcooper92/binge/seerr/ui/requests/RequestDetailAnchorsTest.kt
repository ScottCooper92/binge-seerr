package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.binge.designsystem.layout.LayoutAnchors
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The resolved request page carries the anchors its skeleton declares, `HERO` and `OVERVIEW`, so the
 * geometry check can compare the two (#572). An anchor missing here would read as "never
 * compared" rather than as a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class RequestDetailAnchorsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the resolved page tags the overview the skeleton reserves`() {
        setResolvedPage()

        rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW)).assertIsDisplayed()
    }

    @Test
    fun `the resolved page tags the hero the skeleton reserves`() {
        setResolvedPage()

        rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.HERO)).assertIsDisplayed()
    }

    private fun setResolvedPage() {
        rule.setContent {
            SeerrTheme {
                RequestDetailPage(
                    detail = detail(),
                    onBack = {},
                    onOpen = null,
                    onReport = null,
                    onOpenRequest = {},
                    onOpenUser = {},
                )
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
            actions = RequestActions(),
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
            siblings = emptyList(),
        )
}
