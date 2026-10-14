package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.preview.TvPreviewsOnBlack
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubDownload
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import com.cooper.binge.companion.seerr.ui.hub.HubServer
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubUserLoad
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueListScope
import com.cooper.binge.companion.seerr.ui.issues.IssueSort
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestDownload
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.tv.hub.TvHubActions
import com.cooper.binge.companion.seerr.ui.tv.hub.TvHubBoard
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesBoard
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.ui.tv.settings.PAGE_DISCONNECT
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsActions
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsSheet
import com.cooper.binge.companion.seerr.ui.tv.settings.groupPage
import kotlinx.coroutines.flow.emptyFlow

/*
 * Home's boards, one frame per state and one per focused control. The `@TvPreviewsOnBlack` functions
 * below are IDE-only preview tooling; the fixtures above them are `internal` so the screenshotTest suite
 * (`ui/tv/requests`, `ui/tv/issues`, `ui/tv/settings`) can reuse them instead of duplicating sample data.
 */

internal const val HOUR_MILLIS = 3_600_000L
internal const val DAY_MILLIS = 24 * HOUR_MILLIS
internal val NOW = System.currentTimeMillis()

private val NoHubActions = TvHubActions({}, {}, {})
internal val NoRequestsActions = TvRequestsActions({}, {}, {}, {})
internal val NoIssuesActions = TvIssuesActions({}, {}, {}, {}, {}, {}, {}, {}, {})

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
        permissions = SeerrPermissions(canManageRequests = true, canManageIssues = true, canManageUsers = true),
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
) = HubUiState.Ready(
    server = SampleServer,
    health = health,
    overview = SampleOverview,
    downloading = downloading,
    bingeStatus = BingeStatus.NotConnected,
)

internal fun request(
    id: Int,
    title: String,
    status: SeerrRequestStatusCode,
    seasons: List<Int> = emptyList(),
    mediaStatus: SeerrMediaStatusCode? = null,
    download: RequestDownload? = null,
    now: Long = NOW,
) = RequestItem(
    id = id,
    tmdbId = id,
    mediaType = if (seasons.isEmpty()) RequestMediaType.Movie else RequestMediaType.Tv,
    title = title,
    posterUrl = null,
    year = "2023",
    requestedBy = "ana",
    requestedById = 3,
    requestedAtMillis = now - id * HOUR_MILLIS,
    status = status,
    mediaStatus = mediaStatus,
    download = download,
    seasonNumbers = seasons,
    is4k = id % 2 == 0,
)

internal val SampleRequests =
    listOf(
        request(1, "Heat", SeerrRequestStatusCode.Pending),
        request(
            2,
            "The Bear",
            SeerrRequestStatusCode.Approved,
            seasons = listOf(1, 2),
            mediaStatus = SeerrMediaStatusCode.Processing,
            download = RequestDownload(0.4f, 12, true),
        ),
        request(3, "Dune: Part Two", SeerrRequestStatusCode.Declined),
    )

internal val ManagerScope =
    ModerationScope(permissions = SeerrPermissions(canManageRequests = true), currentUserId = 7, hasBlocklist = true)

internal fun requestsReady() =
    RequestsUiState.Ready(
        filter = RequestFilter.All,
        sort = RequestSort.Added,
        counts = RequestCounts(total = 3, pending = 1, approved = 1, processing = 1, available = 0),
        scope = ManagerScope,
        listVersion = 0,
    )

internal fun <T> rows(items: List<T>) = TvPagedRows(count = items.size, at = { items.getOrNull(it) })

internal fun issue(
    id: Int,
    title: String,
    status: IssueStatus,
    now: Long = NOW,
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
    createdAtMillis = now - id * DAY_MILLIS,
    updatedAtMillis = now - id * HOUR_MILLIS,
    problem = "The subtitles run about two seconds late.",
    problemSeason = 1,
    problemEpisode = 4,
)

internal val SampleIssues = listOf(issue(11, "Severance", IssueStatus.Open), issue(12, "Slow Horses", IssueStatus.Resolved))

internal fun issuesReady(actionItem: IssueItem? = null) =
    IssuesUiState.Ready(
        filter = IssueFilter.Open,
        sort = IssueSort.Added,
        counts = IssueCounts(total = 2, open = 1, resolved = 1),
        scope = IssueListScope(permissions = SeerrPermissions(canManageIssues = true), currentUserId = 7),
        actionItem = actionItem,
    )

internal val SampleSettings =
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

/** Same server, but as an admin sees it: the config is non-null, so the media server row's scan option shows. */
internal val SampleSettingsAdmin = SampleSettings.copy(config = ServerConfig())

/** Same server, with this app's own group: the bug report row and its code. */
internal val SampleSettingsWithApp =
    SampleSettings.copy(
        app =
            AppSettings(
                bugReportUrl = "https://github.com/ScottCooper92/binge-seerr/issues/new?template=bug.yml&app-version=0.1.0+%281%29",
                shakeToReport = true,
            ),
    )

@TvPreviewsOnBlack
@Composable
internal fun TvShellHubPreview() {
    TvHomeShell(page = TvDestination.Hub, onOpenPage = {}, onClosePage = {}) { _, _ ->
        TvHubBoard(state = hub(health = ConnectionHealth.Unreachable), actions = NoHubActions)
    }
}

@TvPreviewsOnBlack
@Composable
internal fun TvHubUnreachablePreview() {
    Box(modifier = Modifier.fillMaxSize()) { TvHubBoard(state = hub(health = ConnectionHealth.Unreachable), actions = NoHubActions) }
}

@TvPreviewsOnBlack
@Composable
internal fun TvHubLoadingPreview() {
    Box(modifier = Modifier.fillMaxSize()) { TvHubBoard(state = HubUiState.Loading, actions = NoHubActions) }
}

@TvPreviewsOnBlack
@Composable
internal fun TvRequestsBoardPreview() {
    TvRequestsRowsBoard(state = requestsReady(), rowsFor = { rows(SampleRequests) }, actions = NoRequestsActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvRequestsEmptyPreview() {
    TvRequestsRowsBoard(state = requestsReady(), rowsFor = { rows(emptyList()) }, actions = NoRequestsActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvRequestsFailedPreview() {
    TvRequestsRowsBoard(
        state = requestsReady(),
        rowsFor = { TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed) },
        actions = NoRequestsActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvIssuesBoardPreview() {
    TvIssuesBoard(
        state = issuesReady(),
        rowsFor = { rows(SampleIssues) },
        events = emptyFlow(),
        actions = NoIssuesActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvIssuesSheetPreview() {
    TvIssuesBoard(
        state = issuesReady(actionItem = SampleIssues.first()),
        rowsFor = { rows(SampleIssues) },
        events = emptyFlow(),
        actions = NoIssuesActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSettingsSheetPreview() {
    TvSettingsSheet(state = SampleSettings, actions = TvSettingsActions(), onDismissRequest = {})
}

@TvPreviewsOnBlack
@Composable
internal fun TvSettingsDisconnectPreview() {
    TvSettingsSheet(state = SampleSettings, actions = TvSettingsActions(), onDismissRequest = {}, initialPage = PAGE_DISCONNECT)
}

/** The admin-only group: its page carries the library scan. */
@TvPreviewsOnBlack
@Composable
internal fun TvSettingsMediaServerPreview() {
    TvSettingsSheet(
        state = SampleSettingsAdmin,
        actions = TvSettingsActions(),
        onDismissRequest = {},
        initialPage = groupPage(stringResource(R.string.server_settings_media_server)),
    )
}
