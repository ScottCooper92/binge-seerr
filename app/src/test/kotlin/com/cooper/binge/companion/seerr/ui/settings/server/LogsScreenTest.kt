package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
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

    private fun setContent(
        level: ServerLogLevel,
        onLevelChange: (ServerLogLevel) -> Unit = {},
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
}
