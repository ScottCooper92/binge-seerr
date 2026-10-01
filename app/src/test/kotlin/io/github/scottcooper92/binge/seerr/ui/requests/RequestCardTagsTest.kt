package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The request card names each of the request's tags, and shows no tag row for a request without any. */
@RunWith(RobolectricTestRunner::class)
class RequestCardTagsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `each tag is shown as its own chip`() {
        setContent(tags = listOf("kids", "anime"))

        composeTestRule.onNodeWithText("kids", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("anime", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a request without tags shows no tag row`() {
        setContent(tags = emptyList())

        assertEquals(0, composeTestRule.onAllNodesWithText("kids", ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Tags").fetchSemanticsNodes().size)
    }

    private fun setContent(tags: List<String>) {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestCard(detail = detail(tags), onClick = null, onOpenUser = {})
            }
        }
    }

    private fun detail(tags: List<String>) =
        RequestDetail(
            item =
                RequestItem(
                    id = 1,
                    tmdbId = 2,
                    mediaType = RequestMediaType.Tv,
                    title = "Show",
                    posterUrl = null,
                    year = "2020",
                    requestedBy = "Ada",
                    requestedById = 7,
                    requestedAtMillis = 0L,
                    status = SeerrRequestStatusCode.Approved,
                    mediaStatus = SeerrMediaStatusCode.Processing,
                    download = null,
                    seasonNumbers = listOf(1),
                    is4k = false,
                ),
            actions = RequestActions(),
            canEdit = false,
            canEditDestination = false,
            backdropUrl = null,
            overview = null,
            modifiedBy = null,
            modifiedById = null,
            viewerId = 7,
            canManageUsers = false,
            updatedAtMillis = null,
            seasons = emptyList(),
            destination = RequestDestination("Sonarr", "HD", "/tv", tags),
            downloads = emptyList(),
            mediaId = null,
            canReportIssue = false,
            webUrl = "https://seerr.example/tv/2",
            mediaServerUrl = null,
            serviceUrl = null,
            media = null,
            siblings = emptyList(),
        )
}
