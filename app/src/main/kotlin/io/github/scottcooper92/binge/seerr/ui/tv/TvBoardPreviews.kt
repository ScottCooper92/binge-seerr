package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.binge.designsystem.tv.preview.TvPreviewsOnBlack
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubDownload
import io.github.scottcooper92.binge.seerr.ui.hub.HubOverview
import io.github.scottcooper92.binge.seerr.ui.hub.HubServer
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.HubUserLoad
import io.github.scottcooper92.binge.seerr.ui.issues.IssueCounts
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListScope
import io.github.scottcooper92.binge.seerr.ui.issues.IssueSort
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationScope
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCounts
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDownload
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.ConnectionSummary
import io.github.scottcooper92.binge.seerr.ui.settings.ServerSummary
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.SignInKind
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TILE_PENDING
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvHubActions
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvHubBoard
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesBoard
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsBoard
import io.github.scottcooper92.binge.seerr.ui.tv.settings.KEY_DISCONNECT
import io.github.scottcooper92.binge.seerr.ui.tv.settings.TvSettingsBoard
import kotlinx.coroutines.flow.emptyFlow

/*
 * The rail's boards, one frame per state and one per focused control. Rendered by the preview pane; this
 * repository has no screenshot suite.
 */

private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 24 * HOUR_MILLIS
private val NOW = System.currentTimeMillis()

private val NoHubActions = TvHubActions({}, {}, {}, {}, {})
private val NoRequestsActions = TvRequestsActions({}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
private val NoIssuesActions = TvIssuesActions({}, {}, {}, {}, {}, {}, {}, {})

private val SampleServer =
    HubServer(
        baseUrl = "http://seerr.lan:5055",
        title = "Living room Jellyseerr",
        variant = SeerrVariant.Jellyseerr,
        versionLabel = "2.7.2",
        updateAvailable = true,
        commitsBehind = 0,
    )

private val SampleOverview =
    HubOverview(
        loaded = true,
        userLoad = HubUserLoad.Loaded,
        permissions = SeerrPermissions(canManageRequests = true, canManageIssues = true),
        movieRequestCount = 128,
        tvRequestCount = 41,
        pendingRequestCount = 6,
        openIssueCount = 2,
        userCount = 9,
        hasIssues = true,
        hasBlocklist = true,
    )

private val SampleDownloads =
    listOf(
        HubDownload(requestId = 1, title = "Heat", posterUrl = null, fraction = 0.62f, totalBytes = null, etaMinutes = 14),
        HubDownload(requestId = 2, title = "The Bear", posterUrl = null, fraction = 0.2f, totalBytes = null, etaMinutes = 55),
    )

private fun hub(
    health: ConnectionHealth = ConnectionHealth.Healthy,
    downloading: List<HubDownload> = SampleDownloads,
) = HubUiState.Ready(server = SampleServer, health = health, overview = SampleOverview, downloading = downloading)

private fun request(
    id: Int,
    title: String,
    status: SeerrRequestStatusCode,
    seasons: List<Int> = emptyList(),
    download: RequestDownload? = null,
) = RequestItem(
    id = id,
    tmdbId = id,
    mediaType = if (seasons.isEmpty()) RequestMediaType.Movie else RequestMediaType.Tv,
    title = title,
    posterUrl = null,
    year = "2023",
    requestedBy = "ana",
    requestedById = 3,
    requestedAtMillis = NOW - id * HOUR_MILLIS,
    status = status,
    mediaStatus = null,
    download = download,
    seasonNumbers = seasons,
    is4k = id % 2 == 0,
)

private val SampleRequests =
    listOf(
        request(1, "Heat", SeerrRequestStatusCode.Pending),
        request(2, "The Bear", SeerrRequestStatusCode.Approved, seasons = listOf(1, 2), download = RequestDownload(0.4f, 12, true)),
        request(3, "Dune: Part Two", SeerrRequestStatusCode.Declined),
    )

private val ManagerScope = ModerationScope(permissions = SeerrPermissions(canManageRequests = true), currentUserId = 7, hasBlocklist = true)

private fun requestsReady(actionItem: RequestItem? = null) =
    RequestsUiState.Ready(
        filter = RequestFilter.All,
        sort = RequestSort.Added,
        counts = RequestCounts(total = 3, pending = 1, approved = 1, processing = 1, available = 0),
        scope = ManagerScope,
        actingIds = emptySet(),
        actionItem = actionItem,
    )

private fun <T> rows(items: List<T>) = TvPagedRows(count = items.size, at = { items.getOrNull(it) })

private fun issue(
    id: Int,
    title: String,
    status: IssueStatus,
) = IssueItem(
    id = id,
    tmdbId = id,
    mediaType = RequestMediaType.Tv,
    title = title,
    posterUrl = null,
    year = "2022",
    type = IssueType.Subtitles,
    status = status,
    reportedBy = "ana",
    reportedById = 3,
    commentCount = 2,
    createdAtMillis = NOW - id * DAY_MILLIS,
    updatedAtMillis = NOW - id * HOUR_MILLIS,
    problem = "The subtitles run about two seconds late.",
    problemSeason = 1,
    problemEpisode = 4,
)

private val SampleIssues = listOf(issue(11, "Severance", IssueStatus.Open), issue(12, "Slow Horses", IssueStatus.Resolved))

private fun issuesReady(actionItem: IssueItem? = null) =
    IssuesUiState.Ready(
        filter = IssueFilter.Open,
        sort = IssueSort.Added,
        counts = IssueCounts(total = 2, open = 1, resolved = 1),
        scope = IssueListScope(permissions = SeerrPermissions(canManageIssues = true), currentUserId = 7),
        actionItem = actionItem,
    )

private val SampleSettings =
    SettingsUiState.Ready(
        connection = ConnectionSummary(baseUrl = "http://seerr.lan:5055", signInKind = SignInKind.Session, userName = "Scott"),
        server =
            ServerSummary(
                title = "Living room Jellyseerr",
                variant = SeerrVariant.Jellyseerr,
                versionLabel = "2.7.2",
                updateAvailable = false,
                commitsBehind = 0,
            ),
        config = null,
    )

@TvPreviewsOnBlack
@Composable
internal fun TvShellHubPreview() {
    TvShellScaffold(selected = TvDestination.Hub, onSelect = {}) {
        TvHubBoard(state = hub(), actions = NoHubActions, initialFocusedTile = TILE_PENDING)
    }
}

@TvPreviewsOnBlack
@Composable
internal fun TvHubUnreachablePreview() {
    Box(modifier = Modifier.fillMaxSize()) { TvHubBoard(state = hub(health = ConnectionHealth.Unreachable), actions = NoHubActions) }
}

@TvPreviewsOnBlack
@Composable
internal fun TvHubUnauthorizedPreview() {
    Box(modifier = Modifier.fillMaxSize()) { TvHubBoard(state = hub(health = ConnectionHealth.Unauthorized), actions = NoHubActions) }
}

@TvPreviewsOnBlack
@Composable
internal fun TvHubLoadingPreview() {
    Box(modifier = Modifier.fillMaxSize()) { TvHubBoard(state = HubUiState.Loading, actions = NoHubActions) }
}

@TvPreviewsOnBlack
@Composable
internal fun TvRequestsBoardPreview() {
    TvRequestsBoard(
        state = requestsReady(),
        rows = rows(SampleRequests),
        events = emptyFlow(),
        actions = NoRequestsActions,
        initialFocusedRowId = 1,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvRequestsSheetPreview() {
    TvRequestsBoard(
        state = requestsReady(actionItem = SampleRequests.first()),
        rows = rows(SampleRequests),
        events = emptyFlow(),
        actions = NoRequestsActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvRequestsEmptyPreview() {
    TvRequestsBoard(state = requestsReady(), rows = rows(emptyList()), events = emptyFlow(), actions = NoRequestsActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvRequestsFailedPreview() {
    TvRequestsBoard(
        state = requestsReady(),
        rows = TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed(rejected = false)),
        events = emptyFlow(),
        actions = NoRequestsActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvIssuesBoardPreview() {
    TvIssuesBoard(
        state = issuesReady(),
        rows = rows(SampleIssues),
        events = emptyFlow(),
        actions = NoIssuesActions,
        initialFocusedRowId = 11,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvIssuesSheetPreview() {
    TvIssuesBoard(
        state = issuesReady(actionItem = SampleIssues.first()),
        rows = rows(SampleIssues),
        events = emptyFlow(),
        actions = NoIssuesActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSettingsBoardPreview() {
    TvSettingsBoard(state = SampleSettings, onEditConnection = {}, onDisconnect = {}, initialListHasFocus = true)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSettingsDisconnectPreview() {
    TvSettingsBoard(
        state = SampleSettings,
        onEditConnection = {},
        onDisconnect = {},
        initialFocusedKey = KEY_DISCONNECT,
        initialFocusedOptionLabel = "Disconnect",
    )
}
