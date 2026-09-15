package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.PagingData
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The blocklist as swipeable source pages: a page composed before it is selected shows its own
 * filter's rows, and a server without the source chips shows the one list it has.
 */
@RunWith(RobolectricTestRunner::class)
class BlocklistPagerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a page swiped into view shows its own filter's rows, and reports the swipe`() {
        val selections = mutableListOf<BlocklistFilter>()
        // Held at All: the page swiped to is composed but never selected.
        setContent(state(BlocklistFilter.All, hasFilters = true)) { selections += it }

        composeTestRule
            .onNode(
                hasScrollToIndexAction() and
                    hasAnyDescendant(hasScrollToIndexAction() and hasAnyDescendant(hasText(titleFor(BlocklistFilter.All)))),
            ).performScrollToIndex(BlocklistFilter.Tagged.ordinal)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(titleFor(BlocklistFilter.Tagged)).assertIsDisplayed()
        assertEquals(listOf(BlocklistFilter.Tagged), selections)
    }

    /** Jellyseerr 2.x has one list and no chips, so there is one page and nothing to swipe to. */
    @Test
    fun `a server without the source chips shows its one list`() {
        setContent(state(BlocklistFilter.All, hasFilters = false))

        composeTestRule.onNodeWithText(titleFor(BlocklistFilter.All)).assertIsDisplayed()
        composeTestRule.onNodeWithText(titleFor(BlocklistFilter.Manual)).assertDoesNotExist()
    }

    private fun setContent(
        state: BlocklistUiState.Ready,
        onFilterChange: (BlocklistFilter) -> Unit = {},
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                BlocklistScreen(
                    state = state,
                    itemsFor = ::rowsFor,
                    events = emptyFlow(),
                    shouldRefresh = { _, _ -> false },
                    actions =
                        BlocklistActions(
                            onBack = {},
                            onFilterChange = onFilterChange,
                            onSearchChange = {},
                            onRemove = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun state(
        filter: BlocklistFilter,
        hasFilters: Boolean,
    ) = BlocklistUiState.Ready(
        filter = filter,
        search = "",
        counts = null,
        listVersion = 0,
        hasFilters = hasFilters,
        canManage = true,
        canBlockCollections = false,
        actingTmdbIds = emptySet(),
        webRoot = "",
    )

    private fun rowsFor(filter: BlocklistFilter): Flow<PagingData<BlocklistItem>> = flowOf(PagingData.from(listOf(item(filter))))

    private fun titleFor(filter: BlocklistFilter) = "Title for ${filter.name}"

    private fun item(filter: BlocklistFilter) =
        BlocklistItem(
            id = filter.ordinal + 1,
            tmdbId = filter.ordinal + 100,
            mediaType = RequestMediaType.Movie,
            title = titleFor(filter),
            posterUrl = null,
            year = null,
            addedBy = null,
            addedAtMillis = null,
            tags = emptyList(),
        )
}
