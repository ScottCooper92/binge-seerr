package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistActions
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistCounts
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistDetailPage
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistDetailUiState
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistFilter
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistScreen
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueListScope
import com.cooper.binge.companion.seerr.ui.issues.IssueSort
import com.cooper.binge.companion.seerr.ui.issues.IssuesActions
import com.cooper.binge.companion.seerr.ui.issues.IssuesScreen
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsActions
import com.cooper.binge.companion.seerr.ui.requests.RequestsScreen
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.users.UserAdmissionActions
import com.cooper.binge.companion.seerr.ui.users.UserSort
import com.cooper.binge.companion.seerr.ui.users.UsersActions
import com.cooper.binge.companion.seerr.ui.users.UsersScreen
import com.cooper.binge.companion.seerr.ui.users.UsersUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The four list screens around their worst-case rows, at font scales 1.3 and 2.0, through the design system's
 * `assertNoTextClipped`: the filter chips with long counts, the sort and search bars, and the blocklist's detail page.
 * The rows and detail pages on their own are in [RowClippingTest] and [PageClippingTest]; this is the screen around them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h8000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListScreensClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the requests screen at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Requests() }

    @Test fun `the requests screen at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Requests() }

    @Test fun `the issues screen at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Issues() }

    @Test fun `the issues screen at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Issues() }

    @Test fun `the users screen at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Users() }

    @Test fun `the users screen at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Users() }

    @Test fun `the blocklist screen at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Blocklist() }

    @Test fun `the blocklist screen at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Blocklist() }

    @Test fun `the blocklist detail page at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { BlocklistDetail() }

    @Test fun `the blocklist detail page at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { BlocklistDetail() }

    @Composable
    private fun Requests() {
        val rows = remember { flowOf(PagingData.from(listOf(worstCaseRequest()))) }
        RequestsScreen(
            state =
                RequestsUiState.Ready(
                    filter = RequestFilter.All,
                    sort = RequestSort.Added,
                    counts = RequestCounts(total = 12_840, pending = 1_204, approved = 9_000, processing = 1_200, available = 7_000),
                    scope = ModerationScope(permissions = SeerrPermissions(canManageRequests = true), currentUserId = 1),
                    listVersion = 1,
                ),
            requestsFor = { rows },
            shouldRefresh = { _, _ -> false },
            actions =
                RequestsActions(
                    onBack = {},
                    onFilterChange = {},
                    onSortChange = {},
                    onOpen = {},
                    onRetryLoad = {},
                    onRefreshCounts = {},
                    onChanged = {},
                    detailSheet = {},
                ),
        )
    }

    @Composable
    private fun Issues() {
        val rows = remember { flowOf(PagingData.from(listOf(worstCaseIssue()))) }
        IssuesScreen(
            state =
                IssuesUiState.Ready(
                    IssueFilter.Open,
                    IssueSort.Added,
                    IssueCounts(12_840, 5_000, 7_000),
                    IssueListScope(permissions = SeerrPermissions(canManageIssues = true), currentUserId = 1),
                ),
            issuesFor = { rows },
            actions =
                IssuesActions(
                    onBack = {},
                    onFilterChange = {},
                    onSortChange = {},
                    onOpen = {},
                    onRetryLoad = {},
                    onRefreshCounts = {},
                ),
        )
    }

    @Composable
    private fun Users() {
        val rows = remember { flowOf(PagingData.from(listOf(worstCaseUser()))) }
        UsersScreen(
            state = UsersUiState.Ready(sort = UserSort.Created, selection = setOf(7), edit = null, offered = emptyList(), canAdmit = true),
            users = rows,
            events = emptyFlow(),
            actions =
                UsersActions(
                    onBack = {},
                    onSortChange = {},
                    onOpen = {},
                    onToggleSelected = {},
                    onClearSelection = {},
                    onStartBulkEdit = {},
                    onTogglePermission = {},
                    onApplyBulkEdit = {},
                    onCancelBulkEdit = {},
                    onRetryLoad = {},
                    admission = UserAdmissionActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                ),
        )
    }

    @Composable
    private fun Blocklist() {
        val rows = remember { flowOf(PagingData.from(listOf(worstCaseBlocklistItem()))) }
        BlocklistScreen(
            state =
                BlocklistUiState.Ready(
                    filter = BlocklistFilter.All,
                    search = "a long search phrase that wraps",
                    counts = BlocklistCounts(all = 12_840, manual = 6_000, tagged = 3_000),
                    listVersion = 1,
                    hasFilters = true,
                    canManage = true,
                    canBlockCollections = true,
                    actingTmdbIds = emptySet(),
                ),
            itemsFor = { rows },
            events = emptyFlow(),
            shouldRefresh = { _, _ -> false },
            actions =
                BlocklistActions(onBack = {}, onFilterChange = {}, onSearchChange = {}, onOpen = {
                    _,
                    _,
                    ->
                }, onRemove = {}, onRetry = {}),
        )
    }

    @Composable
    private fun BlocklistDetail() =
        BlocklistDetailPage(
            state =
                BlocklistDetailUiState(
                    item = worstCaseBlocklistItem(),
                    canManage = true,
                    backdropUrl = null,
                    overview = "A long overview that runs on for several sentences, so the page has a paragraph to wrap at a large font.",
                    webUrl = "https://seerr.example/tv/1396",
                ),
            onBack = {},
            onPrimary = {},
        )
}
