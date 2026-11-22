package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * An issue row is the shared media row: the reporter on top, the affected season or episode after the title, then one line of state,
 * type, kind, year and genre, and the opening line last. The row is one merged node, so each part is found in the unmerged tree.
 */
@RunWith(RobolectricTestRunner::class)
class IssueRowSharedRowTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val item =
        IssueItem(
            id = 9,
            tmdbId = 95_396,
            mediaType = RequestMediaType.Tv,
            title = "Severance",
            posterUrl = null,
            year = "2022",
            type = IssueType.Subtitles,
            status = IssueStatus.Open,
            reportedBy = "Scott",
            reportedById = 1,
            commentCount = 2,
            createdAtMillis = null,
            updatedAtMillis = null,
            problem = "The subtitles drift out of sync",
            problemSeason = 2,
            problemEpisode = 3,
            rating = 8.7f,
            genre = "Drama",
        )

    private fun show(row: IssueItem = item) {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                IssueRow(item = row, onClick = {}, now = 0L)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the reporter is above the title, the title above the status line and the opening line last`() {
        show()

        val byline = rule.onNodeWithText("Scott", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val title = rule.onNodeWithText("Severance", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val status = rule.onNodeWithText("2022", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val opening = rule.onNodeWithText("The subtitles drift out of sync", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue("byline above title", byline.top < title.top)
        assertTrue("title above the status line", title.top < status.top)
        assertTrue("the opening line under the status line", opening.top > status.bottom)
    }

    @Test
    fun `an episode follows the title in short form and the genre shares the status line`() {
        show()

        rule.onNodeWithText("S2 E3", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Drama", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a season alone follows the title as the season number`() {
        show(item.copy(problemEpisode = null))

        rule.onNodeWithText("Season 2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a film carries no season or episode after its title`() {
        show(item.copy(mediaType = RequestMediaType.Movie, problemSeason = null, problemEpisode = null))

        rule.onNodeWithText("S2 E3", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Season 2", useUnmergedTree = true).assertDoesNotExist()
    }
}
