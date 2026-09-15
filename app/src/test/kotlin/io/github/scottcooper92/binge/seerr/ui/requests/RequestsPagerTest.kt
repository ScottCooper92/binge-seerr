package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.robolectric.annotation.Config

/**
 * The requests browser as swipeable filter pages. A pager composes a page before the selection lands
 * on it, so each page must show its own filter's rows, and only the selected page may refresh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RequestsPagerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val selections = mutableListOf<RequestFilter>()
    private val refreshAsks = mutableListOf<RequestFilter>()

    @Test
    fun `a page swiped into view shows its own filter's rows, and reports the swipe`() {
        // The selection is held at All, so the page swiped to is composed but never selected.
        setScreen(selected = RequestFilter.All, followSelection = false)

        pager().performScrollToIndex(RequestFilter.Pending.ordinal)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(titleFor(RequestFilter.Pending)).assertIsDisplayed()
        assertEquals(listOf(RequestFilter.Pending), selections)
    }

    @Test
    fun `only the selected page asks whether to refresh`() {
        setScreen(selected = RequestFilter.All, followSelection = false)

        pager().performScrollToIndex(RequestFilter.Pending.ordinal)
        composeTestRule.waitForIdle()

        assertTrue(refreshAsks.isNotEmpty())
        assertTrue("asked for $refreshAsks", refreshAsks.all { it == RequestFilter.All })
    }

    @Test
    fun `tapping a chip selects its filter`() {
        setScreen(selected = RequestFilter.All)

        // A row's status chip can say "Pending" too; the filter chip is the one that can be selected.
        composeTestRule.onNode(hasText("Pending") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(RequestFilter.Pending, selections.last())
        composeTestRule.onNodeWithText(titleFor(RequestFilter.Pending)).assertIsDisplayed()
    }

    /**
     * The chip row, the pager and each page's list can all scroll to an index. The pager is the one whose
     * scrollable descendant holds the selected page's row.
     */
    private fun pager() =
        composeTestRule.onNode(
            hasScrollToIndexAction() and
                hasAnyDescendant(hasScrollToIndexAction() and hasAnyDescendant(hasText(titleFor(RequestFilter.All)))),
        )

    private fun setScreen(
        selected: RequestFilter,
        followSelection: Boolean = true,
    ) {
        composeTestRule.setContent {
            var filter by remember { mutableStateOf(selected) }
            BingeExpressiveTheme {
                RequestsScreen(
                    state = ready(filter),
                    requestsFor = ::rowsFor,
                    events = emptyFlow(),
                    shouldRefresh = { asked, _ ->
                        refreshAsks += asked
                        false
                    },
                    actions =
                        actions { chosen ->
                            selections += chosen
                            if (followSelection) filter = chosen
                        },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun rowsFor(filter: RequestFilter): Flow<PagingData<RequestItem>> = flowOf(PagingData.from(listOf(item(filter))))

    private fun titleFor(filter: RequestFilter) = "Title for ${filter.name}"

    private fun item(filter: RequestFilter) =
        RequestItem(
            id = filter.ordinal + 1,
            tmdbId = filter.ordinal + 100,
            mediaType = RequestMediaType.Movie,
            title = titleFor(filter),
            posterUrl = null,
            year = null,
            requestedBy = null,
            requestedById = null,
            requestedAtMillis = null,
            status = null,
            mediaStatus = null,
            download = null,
            seasonNumbers = emptyList(),
            is4k = false,
        )

    private fun ready(filter: RequestFilter) =
        RequestsUiState.Ready(
            filter = filter,
            sort = RequestSort.Added,
            counts = null,
            scope = ModerationScope(),
            actingIds = emptySet(),
            listVersion = 1,
        )

    private fun actions(onFilterChange: (RequestFilter) -> Unit) =
        RequestsActions(
            onBack = {},
            onFilterChange = onFilterChange,
            onSortChange = {},
            onOpen = {},
            onOpenActions = {},
            onDismissActions = {},
            onApprove = {},
            onRetry = {},
            onDecline = { _, _ -> },
            onRemove = { _, _ -> },
        )
}
