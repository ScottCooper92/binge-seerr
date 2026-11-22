package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A request row is the shared media row: who asked on top, the seasons after the title, then one line of status, kind, year and genre. */
@RunWith(RobolectricTestRunner::class)
class RequestRowSharedRowTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val item =
        requestItem(id = 4, title = "Severance", mediaType = RequestMediaType.Tv, year = "2022", requestedBy = "Scott")
            .copy(seasonNumbers = listOf(1, 2), rating = 8.7f, genre = "Drama")

    private fun show(
        row: RequestItem = item,
        onManage: (() -> Unit)? = null,
    ) {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestRow(item = row, onClick = {}, onManage = onManage, now = 0L)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the requester is above the title and the title is above the status line`() {
        show()

        val byline = rule.onNodeWithText("Scott", substring = true).getUnclippedBoundsInRoot()
        val title = rule.onNodeWithText("Severance").getUnclippedBoundsInRoot()
        val status = rule.onNodeWithText("2022", substring = true).getUnclippedBoundsInRoot()

        assertTrue("byline above title", byline.top < title.top)
        assertTrue("title above the status line", title.top < status.top)
    }

    @Test
    fun `the seasons follow the title and the status line shows the primary genre`() {
        show()

        rule.onNodeWithText("Seasons 1–2").assertIsDisplayed()
        rule.onNodeWithText("Drama").assertIsDisplayed()
        rule.onNodeWithText("TV show · 2022").assertIsDisplayed()
    }

    @Test
    fun `a request with a download draws its progress under the row`() {
        show(item.copy(download = RequestDownload(fraction = 0.5f, etaMinutes = 12, downloading = true)))

        val title = rule.onNodeWithText("Severance").getUnclippedBoundsInRoot()
        val eta = rule.onNodeWithText("12", substring = true).getUnclippedBoundsInRoot()
        assertTrue("progress under the title", eta.top > title.top)
    }
}
