package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueListScope
import com.cooper.binge.companion.seerr.ui.issues.IssueSort
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesBoard
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEVERANCE = "Severance"
private const val HORSES = "Slow Horses"

/**
 * The issues board under a real D-pad: OK on an issue opens it, whoever is looking, since what the viewer may do is the
 * issue sheet's to offer; and a row past its cap ends in See all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssuesBoardFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val seeAll = mutableListOf<IssueFilter>()
    private val opened = mutableListOf<Int>()

    private val manager = IssueListScope(permissions = SeerrPermissions(canManageIssues = true), currentUserId = 7)
    private val reporter = IssueListScope(permissions = SeerrPermissions(canCreateIssues = true), currentUserId = 3)

    @Test
    fun okOnAnIssueTheViewerManagesOpensIt() {
        setBoard(manager)
        focusFirstRow()

        pressOk()

        assertEquals(listOf(11), opened)
    }

    /** A reporter opens someone else's issue too: the sheet reads it to them, and offers only what they may do. */
    @Test
    fun okOnSomeoneElsesIssueOpensItForAReporter() {
        setBoard(reporter)
        focusFirstRow()
        pressRight()
        row(HORSES).assertIsFocused()

        pressOk()

        assertEquals(listOf(12), opened)
    }

    /** Past the row's cap the row ends in See all, which opens that filter's grid (#1054). */
    @Test
    fun pastTheRowCapTheRowEndsInASeeAllTileThatOpensItsFilter() {
        setBoard(manager, counts = IssueCounts(total = TV_ROW_ITEM_CAP + 1, open = TV_ROW_ITEM_CAP + 1, resolved = 0))
        focusFirstRow()

        pressRight()
        pressRight()
        seeAllTile().assertIsFocused()
        pressOk()

        assertEquals(listOf(IssueFilter.Open), seeAll)
    }

    @Test
    fun aRowWithinTheCapHasNoSeeAllTile() {
        setBoard(manager, counts = IssueCounts(total = TV_ROW_ITEM_CAP, open = TV_ROW_ITEM_CAP, resolved = 0))

        seeAllTile().assertDoesNotExist()
    }

    private fun setBoard(
        scope: IssueListScope,
        items: List<IssueItem> = listOf(issue(11, SEVERANCE, reportedById = 3), issue(12, HORSES, reportedById = 5)),
        counts: IssueCounts? = null,
    ) {
        composeTestRule.setContent {
            val state = remember { IssuesUiState.Ready(filter = IssueFilter.Open, sort = IssueSort.Added, counts = counts, scope = scope) }
            BingeTvTheme {
                TvIssuesBoard(
                    state = state,
                    rowsFor = { filter ->
                        if (filter ==
                            IssueFilter.Open
                        ) {
                            TvPagedRows(count = items.size, at = { items.getOrNull(it) })
                        } else {
                            TvPagedRows(count = 0, at = { null })
                        }
                    },
                    actions =
                        TvIssuesActions(
                            onOpenDetail = { opened += it.id },
                            onSeeAll = { seeAll += it },
                            onRetryLoad = {},
                            onRetryScope = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun focusFirstRow() {
        row(SEVERANCE).requestFocus()
        composeTestRule.waitForIdle()
        row(SEVERANCE).assertIsFocused()
    }

    private fun row(title: String) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())

    private fun seeAllTile() = composeTestRule.onNode(hasContentDescription(string(R.string.tv_see_all)) and isFocusable())

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressOk() = press(Key.DirectionCenter)

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun issue(
        id: Int,
        title: String,
        reportedById: Int,
        commentCount: Int = 0,
        status: IssueStatus = IssueStatus.Open,
    ) = IssueItem(
        id = id,
        tmdbId = id,
        mediaType = RequestMediaType.Tv,
        title = title,
        posterUrl = null,
        year = "2022",
        type = IssueType.Subtitles,
        status = status,
        reportedBy = "ana",
        reportedById = reportedById,
        commentCount = commentCount,
        createdAtMillis = null,
        updatedAtMillis = null,
        problem = null,
        problemSeason = null,
        problemEpisode = null,
    )
}
