package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListScope
import io.github.scottcooper92.binge.seerr.ui.issues.IssueSort
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesBoard
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val SEVERANCE = "Severance"
private const val HORSES = "Slow Horses"

/**
 * The issues board under a real D-pad: a row to its sheet, resolve through its confirm step, and the
 * reporter's own row against someone else's when the viewer is no manager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssuesBoardFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val resolved = mutableListOf<Int>()
    private val deleted = mutableListOf<Int>()
    private val opened = mutableListOf<Int>()
    private var dismissed = 0

    private val manager = IssueListScope(permissions = SeerrPermissions(canManageIssues = true), currentUserId = 7)
    private val reporter = IssueListScope(permissions = SeerrPermissions(canCreateIssues = true), currentUserId = 3)

    @Test
    fun resolvingFromTheRowGoesThroughTheConfirmStep() {
        setBoard(manager)
        focusFirstRow()

        pressOk()
        sheetRow(R.string.tv_issue_resolve).assertIsFocused()
        pressOk()
        sheetRow(com.binge.designsystem.R.string.action_cancel).assertIsFocused()
        pressUp()
        pressOk()

        assertEquals(listOf(11), resolved)
        assertEquals(1, dismissed)
    }

    @Test
    fun cancelOnTheConfirmStepReturnsToTheActions() {
        setBoard(manager)
        focusFirstRow()
        pressOk()
        pressDown()
        sheetRow(R.string.issue_delete).assertIsFocused()
        pressOk()

        pressOk()

        assertTrue(deleted.isEmpty())
        sheetRow(R.string.tv_issue_resolve).assertIsFocused()
    }

    @Test
    fun aReporterMayActOnTheirOwnIssueButNotAnothers() {
        setBoard(reporter)
        focusFirstRow()

        pressOk()
        sheetRow(R.string.tv_issue_resolve).assertIsFocused()
        pressLeft()
        assertEquals(1, dismissed)
        settleFocusRestore()
        row(SEVERANCE).assertIsFocused()

        // The reporter isn't a manager of the other user's issue, so its card has nothing to offer but
        // the read page — no sheet, no confirm step, straight to the page.
        pressRight()
        row(HORSES).assertIsFocused()
        pressOk()
        assertEquals(1, dismissed)
        assertEquals(listOf(12), opened)
    }

    /** The server refuses a reporter's delete once someone has replied, so the sheet does not offer it (#1151). */
    @Test
    fun aReporterIsNotOfferedDeleteOnceSomeoneHasReplied() {
        setBoard(reporter, items = listOf(issue(11, SEVERANCE, reportedById = 3, commentCount = 2)))
        focusFirstRow()

        pressOk()
        sheetRow(R.string.tv_issue_resolve).assertIsFocused()
        composeTestRule.onAllNodes(hasText(string(R.string.issue_delete))).assertCountEquals(0)
        pressDown()
        sheetRow(R.string.tv_issue_read_comments).assertIsFocused()
    }

    @Test
    fun theSheetsReadCommentsRowOpensTheDetailPage() {
        setBoard(manager)
        focusFirstRow()

        pressOk()
        pressDown()
        pressDown()
        sheetRow(R.string.tv_issue_read_comments).assertIsFocused()
        pressOk()

        assertEquals(listOf(11), opened)
        assertTrue(resolved.isEmpty())
    }

    private fun setBoard(
        scope: IssueListScope,
        items: List<IssueItem> = listOf(issue(11, SEVERANCE, reportedById = 3), issue(12, HORSES, reportedById = 5)),
    ) {
        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(IssuesUiState.Ready(filter = IssueFilter.Open, sort = IssueSort.Added, counts = null, scope = scope))
            }
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
                    events = emptyFlow(),
                    actions =
                        TvIssuesActions(
                            onOpenActions = { state = state.copy(actionItem = it) },
                            onDismissActions = {
                                dismissed++
                                state = state.copy(actionItem = null)
                            },
                            onOpenDetail = { opened += it.id },
                            onResolve = { resolved += it.id },
                            onReopen = {},
                            onDelete = { deleted += it.id },
                            onSeeAll = {},
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

    private fun sheetRow(label: Int) = composeTestRule.onNode(hasText(string(label)) and isFocusable())

    private fun pressDown() = press(Key.DirectionDown)

    private fun pressUp() = press(Key.DirectionUp)

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressLeft() = press(Key.DirectionLeft)

    private fun pressOk() = press(Key.DirectionCenter)

    /** The closer hands focus back one frame after the sheet's nodes go, so the walk resumes from the row that opened it. */
    private fun settleFocusRestore() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun issue(
        id: Int,
        title: String,
        reportedById: Int,
        commentCount: Int = 0,
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
        reportedById = reportedById,
        commentCount = commentCount,
        createdAtMillis = null,
        updatedAtMillis = null,
        problem = null,
        problemSeason = null,
        problemEpisode = null,
    )
}
