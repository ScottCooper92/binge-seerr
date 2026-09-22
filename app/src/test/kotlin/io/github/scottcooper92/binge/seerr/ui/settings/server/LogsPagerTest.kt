package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.PagingData
import com.binge.designsystem.theme.BingeExpressiveTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The logs page as swipeable level pages: a page composed before it is selected shows its own
 * level's lines, and following is reported by the selected page rather than by one swiped past.
 */
@RunWith(RobolectricTestRunner::class)
class LogsPagerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a page swiped into view shows its own level's lines, and reports the swipe`() {
        val selections = mutableListOf<LogLevel>()
        val following = mutableListOf<Boolean>()
        // Held at Info: the page swiped to is composed but never selected.
        setContent(LogLevel.Info, onLevelChange = { selections += it }, onFollowingChange = { following += it })

        composeTestRule
            .onNode(
                hasScrollToIndexAction() and
                    hasAnyDescendant(hasScrollToIndexAction() and hasAnyDescendant(hasText(messageFor(LogLevel.Info)))),
            ).performScrollToIndex(LogLevel.Error.ordinal)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(messageFor(LogLevel.Error)).assertIsDisplayed()
        assertEquals(listOf(LogLevel.Error), selections)
        // Only the selected page reports it, and its list is at the top.
        assertTrue(following.all { it })
    }

    private fun setContent(
        level: LogLevel,
        onLevelChange: (LogLevel) -> Unit = {},
        onFollowingChange: (Boolean) -> Unit = {},
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                LogsScreen(
                    state = LogsUiState(level = level),
                    entriesFor = ::linesFor,
                    events = emptyFlow(),
                    actions =
                        LogsActions(
                            onBack = {},
                            onLevelChange = onLevelChange,
                            onSearchChange = {},
                            onFollowingChange = onFollowingChange,
                            onCopy = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun linesFor(level: LogLevel): Flow<PagingData<LogEntry>> = flowOf(PagingData.from(listOf(entry(level))))

    private fun messageFor(level: LogLevel) = "Message for ${level.name}"

    private fun entry(level: LogLevel) =
        LogEntry(
            id = "${level.name}-1",
            timestampMillis = null,
            timestampRaw = "2026-09-15T00:00:00.000Z",
            level = level,
            label = null,
            message = messageFor(level),
            data = null,
        )
}
