package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesGrid
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEVERANCE = "Severance"

/**
 * The issues grid under a real D-pad: OK on a card opens the issue, the way it does on the board's row, so an issue past a
 * row's cap is as manageable as one on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssuesGridFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val opened = mutableListOf<Int>()

    @Test
    fun okOnACardOpensTheIssue() {
        setGrid()
        card().requestFocus()
        composeTestRule.waitForIdle()

        press(Key.DirectionCenter)

        assertEquals(listOf(11), opened)
    }

    /** A see-all grid is an overlay: nothing offers it focus, so it takes it itself, with no `requestFocus()` from outside. */
    @Test
    fun anOpenedGridTakesFocusItself() {
        setGrid()

        card().assertIsFocused()
    }

    private fun setGrid() {
        val items = listOf(issue(11, SEVERANCE))
        composeTestRule.setContent {
            BingeTvTheme {
                TvIssuesGrid(
                    filter = IssueFilter.Open,
                    counts = null,
                    rows = TvPagedRows(count = items.size, at = { items.getOrNull(it) }),
                    actingIds = emptySet(),
                    detailOpen = false,
                    onOpenDetail = { opened += it.id },
                    onRetryLoad = {},
                    onBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun card() = composeTestRule.onNode(hasContentDescription(SEVERANCE) and isFocusable())

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun issue(
        id: Int,
        title: String,
    ) = IssueItem(
        id = id,
        tmdbId = id,
        mediaType = RequestMediaType.Tv,
        title = title,
        posterUrl = null,
        year = "2022",
        type = IssueType.Subtitles,
        status = IssueStatus.Open,
        reportedBy = "ana",
        reportedById = 3,
        commentCount = 0,
        createdAtMillis = null,
        updatedAtMillis = null,
        problem = null,
        problemSeason = null,
        problemEpisode = null,
    )
}
