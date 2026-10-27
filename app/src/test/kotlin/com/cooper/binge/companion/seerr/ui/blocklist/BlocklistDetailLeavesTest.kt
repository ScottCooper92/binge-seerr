package com.cooper.binge.companion.seerr.ui.blocklist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A page leaving on a one-shot event calls the onBack it holds now, not the one it was first composed with: the
 * collector outlives a recomposition, and a later onBack may close over a different route (#1768).
 */
@RunWith(RobolectricTestRunner::class)
class BlocklistDetailLeavesTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `a removal after a recomposition calls the current onBack`() {
        val events = MutableSharedFlow<BlocklistDetailEvent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val calls = mutableListOf<String>()
        var actions by mutableStateOf(actions { calls += "first" })
        composeTestRule.setContent {
            BingeExpressiveTheme {
                BlocklistDetailScreen(state = state(), events = events, actions = actions)
            }
        }
        composeTestRule.waitForIdle()

        actions = actions { calls += "second" }
        composeTestRule.waitForIdle()
        events.tryEmit(BlocklistDetailEvent.Removed)
        composeTestRule.waitForIdle()

        assertEquals(listOf("second"), calls)
    }

    private fun actions(onBack: () -> Unit) = BlocklistDetailActions(onBack = onBack, onUnblock = {})

    private fun state() =
        BlocklistDetailUiState(
            item =
                BlocklistItem(
                    id = 1,
                    tmdbId = 603,
                    mediaType = RequestMediaType.Movie,
                    title = "The Matrix",
                    posterUrl = null,
                    year = "1999",
                    addedBy = "Admin",
                    addedAtMillis = null,
                    tags = emptyList(),
                ),
            canManage = true,
        )
}
