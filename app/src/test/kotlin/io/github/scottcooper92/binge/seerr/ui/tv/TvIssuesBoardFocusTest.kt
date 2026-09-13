package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi", application = android.app.Application::class)
class TvIssuesBoardFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val resolved = mutableListOf<Int>()
    private val deleted = mutableListOf<Int>()
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

        pressDown()
        row(HORSES).assertIsFocused()
        pressOk()
        assertEquals(1, dismissed)
    }

    private fun setBoard(scope: IssueListScope) {
        val items = listOf(issue(11, SEVERANCE, reportedById = 3), issue(12, HORSES, reportedById = 5))
        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(IssuesUiState.Ready(filter = IssueFilter.Open, sort = IssueSort.Added, counts = null, scope = scope))
            }
            BingeTvTheme {
                TvIssuesBoard(
                    state = state,
                    rows = TvPagedRows(count = items.size, at = { items.getOrNull(it) }),
                    events = emptyFlow(),
                    actions =
                        TvIssuesActions(
                            onFilterChange = {},
                            onOpenActions = { state = state.copy(actionItem = it) },
                            onDismissActions = {
                                dismissed++
                                state = state.copy(actionItem = null)
                            },
                            onResolve = { resolved += it.id },
                            onReopen = {},
                            onDelete = { deleted += it.id },
                            onRetryLoad = {},
                            onReconnect = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun focusFirstRow() {
        composeTestRule.onNode(hasTextExactly(string(R.string.issues_filter_open)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        pressDown()
        row(SEVERANCE).assertIsFocused()
    }

    private fun row(title: String) = composeTestRule.onNode(hasText(title) and isFocusable())

    private fun sheetRow(label: Int) = composeTestRule.onNode(hasText(string(label)) and isFocusable())

    private fun pressDown() = press(Key.DirectionDown)

    private fun pressUp() = press(Key.DirectionUp)

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
        commentCount = 0,
        createdAtMillis = null,
        updatedAtMillis = null,
        problem = null,
        problemSeason = null,
        problemEpisode = null,
    )
}
