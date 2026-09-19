package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.ui.issues.IssueComment
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetail
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailUiState
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListScope
import io.github.scottcooper92.binge.seerr.ui.issues.IssueSort
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssueDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssueDetailScreen
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesBoard
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun okOnAnUnmanageableRowReachesThePageAndBackReturnsFocusToTheRow() {
        val item = issue(11, SEVERANCE)
        composeTestRule.setContent {
            var openId by remember { mutableStateOf<Int?>(null) }
            // No manage permission at all, so the row has nothing to offer but the read page.
            val scope = IssueListScope(permissions = SeerrPermissions(), currentUserId = 9)
            BingeTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvIssuesBoard(
                        state =
                            IssuesUiState.Ready(filter = IssueFilter.Open, sort = IssueSort.Added, counts = null, scope = scope),
                        rows = TvPagedRows(count = 1, at = { item }),
                        events = emptyFlow(),
                        openIssueId = openId,
                        actions =
                            TvIssuesActions(
                                onFilterChange = {},
                                onSortChange = {},
                                onOpenActions = {},
                                onDismissActions = {},
                                onOpenDetail = { openId = it.id },
                                onResolve = {},
                                onReopen = {},
                                onDelete = {},
                                onRetryLoad = {},
                                onReconnect = {},
                            ),
                    )
                    if (openId != null) {
                        TvIssueDetailScreen(
                            state = IssueDetailUiState.Ready(detail(item, listOf(comment(2, "mod", "We'll check episode 4.")))),
                            actions = TvIssueDetailActions(onBack = { openId = null }, onRetry = {}),
                        )
                    }
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

    private fun row(title: String) = composeTestRule.onNode(hasText(title) and isFocusable())

    private fun pressOk() = press(Key.DirectionCenter)

    private fun pressDown() = press(Key.DirectionDown)

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** The board offers focus back to the row one frame after the open issue id clears. */
    private fun settleFocusRestore() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
