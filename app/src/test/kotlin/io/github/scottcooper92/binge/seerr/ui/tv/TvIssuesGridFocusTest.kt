package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
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
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesGrid
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesGridManagement
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val SEVERANCE = "Severance"

/**
 * The issues grid under a real D-pad: OK on a card a manager may act on opens the actions sheet, the way
 * it does on the board's row, so an issue past a row's cap can still be resolved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssuesGridFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val resolved = mutableListOf<Int>()
    private val opened = mutableListOf<Int>()

    @Test
    fun okOnAManagedCardOpensTheActionsSheetAndResolves() {
        setGrid(SeerrPermissions(canManageIssues = true))
        card().requestFocus()
        composeTestRule.waitForIdle()

        press(Key.DirectionCenter)
        sheetRow(R.string.tv_issue_resolve).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertEquals(listOf(11), resolved)
        assertEquals(emptyList<Int>(), opened)
    }

    @Test
    fun okOnACardTheViewerMayNotActOnOpensItsPage() {
        setGrid(SeerrPermissions())
        card().requestFocus()
        composeTestRule.waitForIdle()

        press(Key.DirectionCenter)

        assertEquals(listOf(11), opened)
        assertEquals(emptyList<Int>(), resolved)
    }

    private fun setGrid(permissions: SeerrPermissions) {
        val items = listOf(issue(11, SEVERANCE))
        composeTestRule.setContent {
            var actionItem by remember { mutableStateOf<IssueItem?>(null) }
            BingeTvTheme {
                TvIssuesGrid(
                    filter = IssueFilter.Open,
                    counts = null,
                    rows = TvPagedRows(count = items.size, at = { items.getOrNull(it) }),
                    actingIds = emptySet(),
                    management =
                        TvIssuesGridManagement(
                            scope = IssueListScope(permissions = permissions, currentUserId = 7),
                            actionItem = actionItem,
                            onOpenActions = { actionItem = it },
                            onDismissActions = { actionItem = null },
                            onResolve = { resolved += it.id },
                            onReopen = {},
                            onDelete = {},
                        ),
                    detailOpen = false,
                    onOpenDetail = { opened += it.id },
                    onRetryLoad = {},
                    onReconnect = {},
                    onBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun card() = composeTestRule.onNode(hasContentDescription(SEVERANCE) and isFocusable())

    private fun sheetRow(label: Int) = composeTestRule.onNode(hasText(string(label)) and isFocusable())

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

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
