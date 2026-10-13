package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.issues.IssueComment
import com.cooper.binge.companion.seerr.ui.issues.IssueDetail
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueListScope
import com.cooper.binge.companion.seerr.ui.issues.IssueSort
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueDetailActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesBoard
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

private const val SEVERANCE = "Severance"

/**
 * The board's row through to its read-only detail page and back, under a real Back key, and the
 * thread's own scroll: a row a viewer cannot manage has nothing to offer but the read page, and once
 * there the D-pad walks a long thread comment by comment, bringing each one on screen in turn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssueDetailFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    @Test
    fun okOnAnUnmanageableRowReachesThePageAndBackReturnsFocusToTheRow() {
        val item = issue(11, SEVERANCE)
        composeTestRule.setContent {
            var openId by remember { mutableStateOf<Int?>(null) }
            // No manage permission at all, so the row has nothing to offer but the read page.
            val scope = IssueListScope(permissions = SeerrPermissions(), currentUserId = 9)
            BingeTvTheme {
                // The page is the shell's overlay, as the app hosts it, so its close goes through the shell's hand-off.
                BoardInShell(
                    destination = TvDestination.Issues,
                    overlay =
                        openId?.let {
                            {
                                TvIssueDetailScreen(
                                    state = IssueDetailUiState.Ready(detail(item, listOf(comment(2, "mod", "We'll check episode 4.")))),
                                    actions = TvIssueDetailActions(onBack = { openId = null }, onRetry = {}),
                                )
                            }
                        },
                ) {
                    TvIssuesBoard(
                        state =
                            IssuesUiState.Ready(filter = IssueFilter.Open, sort = IssueSort.Added, counts = null, scope = scope),
                        rowsFor = { filter ->
                            if (filter ==
                                IssueFilter.Open
                            ) {
                                TvPagedRows(count = 1, at = { item })
                            } else {
                                TvPagedRows(count = 0, at = { null })
                            }
                        },
                        events = emptyFlow(),
                        actions =
                            TvIssuesActions(
                                onOpenActions = {},
                                onDismissActions = {},
                                onOpenDetail = { openId = it.id },
                                onResolve = {},
                                onReopen = {},
                                onDelete = {},
                                onSeeAll = {},
                                onRetryLoad = {},
                                onRetryScope = {},
                            ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        row(SEVERANCE).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        composeTestRule.onNode(hasText("We'll check episode 4.") and isFocusable()).assertIsFocused()

        pressBack()
        settleFocusRestore()
        row(SEVERANCE).assertIsFocused()
    }

    @Test
    fun aFailedLoadLandsOnRetry() {
        var retries = 0
        composeTestRule.setContent {
            BingeTvTheme {
                TvIssueDetailScreen(
                    state = IssueDetailUiState.Error(SeerrError.Server),
                    actions = TvIssueDetailActions(onBack = {}, onRetry = { retries++ }),
                )
            }
        }
        composeTestRule.waitForIdle()

        val retry = string(DesR.string.error_kind_retry)
        composeTestRule.onNode(hasText(retry) and isFocusable()).assertIsFocused()
        pressOk()
        assertEquals(1, retries)
    }

    @Test
    fun theDPadWalksALongThreadCommentByComment() {
        val comments = (1..8).map { comment(it, "mod$it", "Update $it: still checking the timing.") }
        composeTestRule.setContent {
            BingeTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvIssueDetailScreen(
                        state = IssueDetailUiState.Ready(detail(issue(11, SEVERANCE), comments)),
                        actions = TvIssueDetailActions(onBack = {}, onRetry = {}),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Arrival lands on the thread's first comment.
        composeTestRule.onNode(hasText("Update 1: still checking the timing.") and isFocusable()).assertIsFocused()

        repeat(comments.size - 1) { pressDown() }

        // The walk reached — and scrolled into view — the last comment in the thread.
        composeTestRule.onNode(hasText("Update 8: still checking the timing.") and isFocusable()).assertIsFocused()
    }

    @Test
    fun anEmptyThreadLandsOnItsNoCommentsLineAndNothingIsAnnouncedDisabled() {
        composeTestRule.setContent {
            BingeTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvIssueDetailScreen(
                        state = IssueDetailUiState.Ready(detail(issue(11, SEVERANCE), emptyList())),
                        actions = TvIssueDetailActions(onBack = {}, onRetry = {}),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText(string(R.string.issue_no_comments)) and isFocusable()).assertIsFocused()
        composeTestRule.onAllNodes(isNotEnabled()).assertCountEquals(0)
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
        problem = "The subtitles run late.",
        problemSeason = 1,
        problemEpisode = 4,
    )

    private fun comment(
        id: Int,
        author: String,
        message: String,
    ) = IssueComment(
        id = id,
        author = author,
        authorId = id,
        isAdmin = false,
        message = message,
        createdAtMillis = null,
        isMine = false,
    )

    private fun detail(
        item: IssueItem,
        comments: List<IssueComment>,
    ) = IssueDetail(
        item = item,
        report = comment(1, "ana", item.problem.orEmpty()),
        comments = comments,
        canComment = true,
        canManage = false,
        canResolve = false,
        canDelete = false,
        webUrl = "https://seerr.example/issues/${item.id}",
        mediaServerUrl = null,
        serviceUrl = null,
        currentUserName = "ana",
    )

    private fun row(title: String) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())

    private fun pressOk() = press(Key.DirectionCenter)

    private fun pressDown() = press(Key.DirectionDown)

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** The shell hands focus back into the board once the page above the rail has closed. */
    private fun settleFocusRestore() = composeTestRule.settleShellHandOff()

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
