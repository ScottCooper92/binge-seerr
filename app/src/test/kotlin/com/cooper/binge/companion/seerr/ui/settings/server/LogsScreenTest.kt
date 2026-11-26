package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.homelab.core.presentation.diagnostics.ServerLogEntry
import com.cooper.homelab.core.presentation.diagnostics.ServerLogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

private const val RETRY_LABEL = "Couldn't load more. Tap to retry."

/** A static list the pager has read to its end, so the reader does not keep asking for a next page that never comes. */
private val ALL_LOADED = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))

/**
 * The logs page over the design system's log reader: the selected level's lines show, a level chip reports its level, and the list
 * reports that it is at the top so the page can follow the newest lines.
 */
@RunWith(RobolectricTestRunner::class)
class LogsScreenTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `the selected level's lines show`() {
        setContent(ServerLogLevel.Warn)

        composeTestRule.onNodeWithText(messageFor(ServerLogLevel.Warn)).assertIsDisplayed()
    }

    @Test
    fun `a level chip reports its level`() {
        val selections = mutableListOf<ServerLogLevel>()
        setContent(ServerLogLevel.Info, onLevelChange = { selections += it })

        composeTestRule.onNodeWithText("Error").performClick()

        assertEquals(listOf(ServerLogLevel.Error), selections)
    }

    @Test
    fun `a list at its top reports it, so the page follows the newest lines`() {
        val following = mutableListOf<Boolean>()
        setContent(ServerLogLevel.Info, onFollowingChange = { following += it })

        assertTrue(following.isNotEmpty() && following.all { it })
    }

    @Test
    fun `try again on a failed next page asks the source again`() {
        val source = FailingSecondPageSource()
        setContent(
            ServerLogLevel.Info,
            entries = { Pager(PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1)) { source }.flow },
        )
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(RETRY_LABEL).fetchSemanticsNodes().isNotEmpty()
        }
        val loadsBefore = source.loads

        composeTestRule.onNodeWithText(RETRY_LABEL).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { source.loads > loadsBefore }
    }

    private fun setContent(
        level: ServerLogLevel,
        entries: (ServerLogLevel) -> Flow<PagingData<ServerLogEntry>> = ::linesFor,
        onLevelChange: (ServerLogLevel) -> Unit = {},
        onFollowingChange: (Boolean) -> Unit = {},
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                LogsScreen(
                    state = LogsUiState(level = level),
                    entriesFor = entries,
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

    private fun linesFor(level: ServerLogLevel): Flow<PagingData<ServerLogEntry>> =
        flowOf(PagingData.from(listOf(entry(level)), sourceLoadStates = ALL_LOADED))

    private fun messageFor(level: ServerLogLevel) = "Message for ${level.name}"

    private fun entry(level: ServerLogLevel) =
        ServerLogEntry(
            id = "${level.name}-1",
            timestampMillis = null,
            timestampRaw = "2026-09-15T00:00:00.000Z",
            level = level,
            label = null,
            message = messageFor(level),
            data = null,
        )

    /** Serves one line, then fails every later page, counting each load. */
    private inner class FailingSecondPageSource : PagingSource<Int, ServerLogEntry>() {
        var loads = 0

        override fun getRefreshKey(state: PagingState<Int, ServerLogEntry>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ServerLogEntry> {
            loads++
            val page = params.key ?: 0
            return if (page == 0) {
                LoadResult.Page(listOf(entry(ServerLogLevel.Info)), prevKey = null, nextKey = 1)
            } else {
                LoadResult.Error(IOException("offline"))
            }
        }
    }
}
