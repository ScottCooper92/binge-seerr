package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.ui.hub.HubDownload
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import com.cooper.binge.companion.seerr.ui.hub.HubQuota
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.hub.HubServer
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubUserLoad
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.tv.NoIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.NoRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.hub.TvAccountSheet
import com.cooper.binge.companion.seerr.ui.tv.hub.TvHubActions
import com.cooper.binge.companion.seerr.ui.tv.hub.TvHubBoard
import com.cooper.binge.companion.seerr.ui.tv.issue
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueActionsSheet
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesBoard
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesGrid
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesGridManagement
import com.cooper.binge.companion.seerr.ui.tv.issuesReady
import com.cooper.binge.companion.seerr.ui.tv.request
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsGrid
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.ui.tv.requestsReady
import com.cooper.binge.companion.seerr.ui.tv.rows
import com.cooper.binge.companion.seerr.ui.users.UserDetail
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val NOW = 1_770_000_000_000L
private const val LONG_SYNOPSIS =
    "A crew, a cop, and a city that does not remember either of them, until the pressure of the job moves on, and the " +
        "distance between the people who began it and the ones still doing it stops being something anyone can measure."

/**
 * The television's boards with long titles, names and counts, at font scales 1.3 and 2.0, through the design system's
 * `assertNoTextClipped`: the hub, the request and issue boards and their grids, and the account board. A board that
 * scrolls is checked on its first screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvBoardsClippingTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test fun `an unreachable hub at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Hub(ConnectionHealth.Unreachable) }

    @Test fun `the requests board at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { RequestsBoard() }

    @Test fun `the issues board at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { IssuesBoard() }

    @Test fun `the requests grid at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { RequestsGrid() }

    @Test fun `the issues grid at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { IssuesGrid() }

    @Test fun `the account sheet at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Account() }

    private fun longRequest(index: Int) =
        request(
            id = index,
            title = LONG_TITLE,
            status = SeerrRequestStatusCode.Approved,
            seasons = listOf(1, 2, 4, 5, 6),
            mediaStatus = SeerrMediaStatusCode.PartiallyAvailable,
            now = NOW,
        ).copy(requestedBy = LONG_NAME, overview = LONG_SYNOPSIS, certification = "TV-MA")

    private fun longIssue(index: Int) =
        issue(index + 10, LONG_TITLE, IssueStatus.Open, now = NOW).copy(reportedBy = LONG_NAME, problem = LONG_SYNOPSIS, commentCount = 128)

    @Composable
    private fun Hub(health: ConnectionHealth = ConnectionHealth.Healthy) =
        TvHubBoard(
            state =
                HubUiState.Ready(
                    server =
                        HubServer(
                            baseUrl = "https://a-rather-long-hostname.living-room.example.org:5055",
                            title = "Alexandria Fitzgerald-Montgomery's Front Room Jellyseerr",
                            variant = SeerrVariant.Seerr,
                            versionLabel = "3.1.0-develop.2026.10.12",
                            updateAvailable = true,
                            commitsBehind = 1_204,
                        ),
                    health = health,
                    overview =
                        HubOverview(
                            loaded = true,
                            userLoad = HubUserLoad.Loaded,
                            account = HubAccount(id = 1, name = LONG_NAME, isAdmin = true, avatarUrl = null),
                            quota = HubQuota(HubQuotaBucket(10_000, 0, 365), HubQuotaBucket(8_000, 5_000, 365)),
                            permissions = SeerrPermissions(isAdmin = true),
                            movieRequestCount = 12_840,
                            tvRequestCount = 9_471,
                            pendingRequestCount = 1_204,
                            openIssueCount = 318,
                            userCount = 2_048,
                            blocklistCount = 4_096,
                            hasIssues = true,
                            hasBlocklist = true,
                        ),
                    downloading = listOf(HubDownload(1, LONG_TITLE, null, 0.42f, 46_000_000_000_000L, 1_440)),
                    bingeStatus = BingeStatus.NotConnected,
                ),
            actions = TvHubActions(onRetry = {}, onReconnect = {}, onDisconnect = {}),
        )

    @Composable
    private fun RequestsBoard() =
        TvRequestsRowsBoard(
            state =
                requestsReady().copy(
                    counts = RequestCounts(total = 12_840, pending = 1_204, approved = 9_000, processing = 1_200, available = 7_000),
                ),
            rowsFor = { filter ->
                if (filter == RequestFilter.Pending ||
                    filter == RequestFilter.Approved
                ) {
                    rows(listOf(longRequest(1), longRequest(2)))
                } else {
                    rows(emptyList())
                }
            },
            actions = NoRequestsActions,
        )

    @Composable
    private fun RequestsGrid() =
        TvRequestsGrid(
            filter = RequestFilter.Pending,
            counts = RequestCounts(total = 12_840, pending = 1_204, approved = 9_000, processing = 1_200, available = 7_000),
            rows = rows((1..8).map { longRequest(it) }),
            detailOpen = false,
            onOpenDetail = {},
            onRetryLoad = {},
            onBack = {},
            now = NOW,
        )

    @Composable
    private fun IssuesBoard() =
        TvIssuesBoard(
            state = issuesReady().copy(counts = IssueCounts(total = 12_840, open = 5_000, resolved = 7_000)),
            rowsFor = { filter -> if (filter == IssueFilter.Open) rows(listOf(longIssue(1), longIssue(2))) else rows(emptyList()) },
            events = emptyFlow(),
            actions = NoIssuesActions,
            now = NOW,
        )

    @Composable
    private fun IssueSheet() =
        TvIssueActionsSheet(
            item = longIssue(1),
            canDelete = true,
            onResolve = {},
            onReopen = {},
            onDelete = {},
            onOpenDetail = {},
            onDismiss = {},
        )

    @Composable
    private fun IssuesGrid() =
        TvIssuesGrid(
            filter = IssueFilter.Open,
            counts = IssueCounts(total = 12_840, open = 5_000, resolved = 7_000),
            rows = rows((1..6).map { longIssue(it) }),
            actingIds = emptySet(),
            management =
                TvIssuesGridManagement(
                    scope = null,
                    actionItem = null,
                    onOpenActions = {},
                    onDismissActions = {},
                    onResolve = {},
                    onReopen = {},
                    onDelete = {},
                ),
            detailOpen = false,
            onOpenDetail = {},
            onRetryLoad = {},
            onBack = {},
            now = NOW,
        )

    @Composable
    private fun Account() =
        TvAccountSheet(
            detail =
                UserDetailUiState.Ready(
                    UserDetail(
                        item = worstCaseUser(),
                        permissions = emptySet(),
                        quota = HubQuota(HubQuotaBucket(10_000, 0, 365), HubQuotaBucket(8_000, 5_000, 365)),
                        watch = null,
                        watchlist = emptyList(),
                        isSelf = true,
                        canEditSettings = true,
                        canDelete = false,
                        serverUrl = "http://seerr.lan:5055",
                        webUrl = "http://seerr.lan:5055/users/7",
                    ),
                ),
            accountFailed = false,
            onRetry = {},
            onDismissRequest = {},
        )
}
