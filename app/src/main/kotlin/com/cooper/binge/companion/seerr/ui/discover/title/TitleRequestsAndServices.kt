package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.formatRanges
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.title.TitleRequest
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.requests.DetailDownload
import com.cooper.binge.companion.seerr.ui.requests.OpenTarget
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailViewModel
import com.cooper.binge.companion.seerr.ui.requests.RequestSummary
import com.cooper.binge.companion.seerr.ui.requests.SeasonState
import com.cooper.binge.companion.seerr.ui.requests.ServiceItem
import com.cooper.binge.companion.seerr.ui.requests.ServiceKind
import com.cooper.binge.companion.seerr.ui.requests.WatchStats
import com.cooper.binge.companion.seerr.ui.requests.canResolve
import com.cooper.binge.companion.seerr.ui.requests.mediaServerBaseUrl
import com.cooper.binge.companion.seerr.ui.requests.openServiceApp
import com.cooper.binge.companion.seerr.ui.requests.openTarget
import com.cooper.binge.companion.seerr.ui.requests.serviceApps
import com.cooper.binge.companion.seerr.ui.state.RequestStateChip
import com.cooper.binge.companion.seerr.ui.state.externalItem
import com.cooper.binge.companion.seerr.ui.state.formatFileSize
import com.cooper.binge.companion.seerr.ui.state.labelRes
import com.cooper.binge.companion.seerr.ui.state.stateLabel
import kotlinx.coroutines.delay
import com.binge.designsystem.R as DesR

/** The two sheets over a title page besides the request's own: the list of its requests, and the services it sits on. */
@Composable
internal fun TitleRequestsAndServices(
    requestsOpen: Boolean,
    servicesOpen: Boolean,
    requests: List<TitleRequest>,
    servicesContent: List<ServiceGroup>,
    mediaServerName: String?,
    actions: TitlePageActions,
) {
    if (requestsOpen) TitleRequestsSheet(requests, actions.onPickRequest, actions.onBackFromRequests, actions.onCloseRequests)
    if (servicesOpen) TitleServicesSheet(servicesContent, mediaServerName, actions.onCloseServiceInfo)
}

/** One request's page, read through the view model its own sheet shares, so a list and a sheet fetch it once. Null while it loads. */
@Composable
internal fun rememberRequestDetail(requestId: Int): RequestDetail? {
    val viewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(
            key = "request-detail-$requestId",
            creationCallback = { factory -> factory.create(requestId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    return (state as? RequestDetailUiState.Ready)?.detail
}

/** One row per request on the title: who made it, its status, the 4K tag and the seasons. A row opens that request's sheet. */
@Composable
internal fun TitleRequestsSheet(
    requests: List<TitleRequest>,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val newest = requests.maxOfOrNull { it.id }
    val summaries =
        newest?.let { rememberRequestDetail(it) }?.summaries()
            ?: requests.map { RequestSummary(it.id, it.status, null, null, it.is4k, emptyList()) }
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                    .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(DesR.string.cd_navigate_back))
                }
                Text(stringResource(R.string.title_requests_sheet_title), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                ItemGroup(
                    title = null,
                    rows =
                        summaries.sortedByDescending { it.id }.map { request ->
                            ListItem(
                                icon = Icons.Filled.Person,
                                label = request.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                                detail =
                                    listOfNotNull(
                                        stringResource(R.string.settings_service_4k).takeIf { request.is4k },
                                        request.seasonNumbers.takeIf { it.isNotEmpty() }?.let {
                                            pluralStringResource(R.plurals.requests_seasons, it.size, it.formatRanges())
                                        },
                                    ).joinToString(" · ").ifBlank { null },
                                trailingContent = request.status?.let { status -> { RequestStateChip(status = status) } },
                                onClick = { onPick(request.id) },
                            )
                        },
                )
            }
        }
    }
}

/** One line of a services group, before it is drawn: what the sheet shows and what decides whether the Services action shows. */
internal sealed interface ServiceRow {
    data class Destination(
        val is4k: Boolean,
        val label: String,
        val folder: String?,
    ) : ServiceRow

    data class Download(
        val download: DetailDownload,
    ) : ServiceRow

    data class Season(
        val season: SeasonState,
    ) : ServiceRow

    data class Watch(
        val stats: WatchStats,
    ) : ServiceRow

    data class Open(
        val target: OpenTarget,
    ) : ServiceRow
}

/** Radarr or Sonarr, or the media server, with the rows it has. A group with no rows is never made. */
internal class ServiceGroup(
    val kind: ServiceKind,
    val rows: List<ServiceRow>,
)

/**
 * What the Services sheet shows for a title, from its newest request's page: the groups that have a row. The page asks the same
 * function whether the Services action is there at all, so the action and the sheet cannot disagree.
 */
internal fun servicesContent(
    details: List<RequestDetail>,
    kind: TitleKind,
    tmdbId: Int,
    resolves: (Intent) -> Boolean,
): List<ServiceGroup> {
    val detail = details.maxByOrNull { it.item.id } ?: return emptyList()
    val arrKind = if (kind == TitleKind.Series) ServiceKind.Sonarr else ServiceKind.Radarr
    val arrRows: List<ServiceRow> =
        details
            .sortedByDescending { it.item.id }
            .distinctBy { it.item.is4k }
            .sortedBy { it.item.is4k }
            .mapNotNull { d ->
                d.destination
                    ?.let {
                        ServiceRow.Destination(
                            d.item.is4k,
                            listOfNotNull(it.serverName, it.profileName).joinToString(" · "),
                            it.rootFolder,
                        )
                    }?.takeIf { it.label.isNotBlank() }
            } +
            detail.downloads.map { ServiceRow.Download(it) } +
            detail.seasons.map { ServiceRow.Season(it) } +
            listOfNotNull(arrKind.openTarget(ServiceItem(), detail.serviceUrl, serviceApps()) { false }?.let { ServiceRow.Open(it) })
    val item =
        ServiceItem(
            serverUrl = mediaServerBaseUrl(detail.mediaServerUrl),
            itemId = detail.mediaServerItem?.itemId,
            tmdbType = if (kind == TitleKind.Series) "tv" else "movie",
            tmdbId = tmdbId,
        )
    val serverRows =
        listOfNotNull(
            detail.media
                ?.instances
                ?.firstNotNullOfOrNull { it.watch }
                ?.let { ServiceRow.Watch(it) },
            ServiceKind.Jellyfin.openTarget(item, detail.mediaServerUrl, serviceApps(), resolves)?.let { ServiceRow.Open(it) },
        )
    return listOfNotNull(
        ServiceGroup(arrKind, arrRows).takeIf { arrRows.isNotEmpty() },
        ServiceGroup(ServiceKind.Jellyfin, serverRows).takeIf { serverRows.isNotEmpty() },
    )
}

/**
 * The newest request's page, read through the view model its sheet shares, as the groups the Services sheet would show. Empty while
 * it loads, and where the title has no request. The page's Services action and the sheet both read this.
 */
@Composable
internal fun rememberServicesContent(
    requests: List<TitleRequest>,
    kind: TitleKind,
    tmdbId: Int,
): List<ServiceGroup> = rememberServicesState(requests, kind, tmdbId).groups

/** The services the page's action is drawn from, and whether they are known: the requests' pages have all come back, or the wait for them is over. */
internal class ServicesState(
    val groups: List<ServiceGroup>,
    val settled: Boolean,
)

/**
 * The services content with whether it has settled. The page holds its loading frame until it has, so the Services action is in the first
 * frame that shows and does not push the other actions sideways when it arrives. The wait is bounded, so a slow request page never holds it.
 */
@Composable
internal fun rememberServicesState(
    requests: List<TitleRequest>,
    kind: TitleKind,
    tmdbId: Int,
): ServicesState {
    val context = LocalContext.current
    // A preview has no activity to build a view model in, so a frame draws a title with no services.
    if (LocalInspectionMode.current) return ServicesState(emptyList(), settled = true)
    val details = requests.sortedByDescending { it.id }.map { rememberRequestDetail(it.id) }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SERVICES_SETTLE_MS)
        waited = true
    }
    val groups = remember(details, kind, tmdbId) { servicesContent(details.filterNotNull(), kind, tmdbId) { context.canResolve(it) } }
    return ServicesState(groups, settled = waited || details.all { it != null })
}

/** How long a title page waits for its requests' pages before it shows without Services. */
private const val SERVICES_SETTLE_MS = 600L

@Composable
internal fun TitleServicesSheet(
    groups: List<ServiceGroup>,
    mediaServerName: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                    .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            groups.forEach { group ->
                val name =
                    when (group.kind) {
                        ServiceKind.Radarr -> stringResource(R.string.media_client_radarr)
                        ServiceKind.Sonarr -> stringResource(R.string.media_client_sonarr)
                        else -> mediaServerName ?: stringResource(R.string.request_open_media_server)
                    }
                ItemGroup(title = name, rows = group.rows.map { row -> serviceListItem(row, name, context) })
            }
        }
    }
}

@Composable
private fun serviceListItem(
    row: ServiceRow,
    serviceName: String,
    context: Context,
): ListItem =
    when (row) {
        is ServiceRow.Destination ->
            ListItem(
                icon = Icons.Filled.Dns,
                label = stringResource(if (row.is4k) R.string.settings_service_4k else R.string.services_standard) + " · " + row.label,
                detail = row.folder,
                clickable = false,
                destination = ListItemDestination.Action,
                trailingContent = {},
            )
        is ServiceRow.Download ->
            ListItem(
                icon = Icons.Filled.Download,
                label = row.download.title ?: stringResource(R.string.request_downloads),
                detail =
                    listOfNotNull(
                        "${(row.download.fraction * PERCENT).toInt()}%",
                        row.download.totalBytes?.let(::formatFileSize),
                        row.download.etaMinutes?.let { "~$it min" },
                    ).joinToString(" · "),
                clickable = false,
                destination = ListItemDestination.Action,
                trailingContent = {},
            )
        is ServiceRow.Season ->
            ListItem(
                icon = Icons.Filled.Movie,
                label = row.season.name ?: stringResource(R.string.request_season_number, row.season.number),
                detail = row.season.status?.let { stringResource(it.stateLabel(row.season.download).labelRes) },
                clickable = false,
                destination = ListItemDestination.Action,
                trailingContent = {},
            )
        is ServiceRow.Watch ->
            ListItem(
                icon = Icons.Filled.Visibility,
                label = stringResource(R.string.services_plays),
                detail =
                    "${row.stats.playCount} · 7d ${row.stats.playCount7Days} · 30d ${row.stats.playCount30Days}" +
                        row.stats.users
                            .takeIf { it.isNotEmpty() }
                            ?.let { " · " + it.joinToString(", ") }
                            .orEmpty(),
                clickable = false,
                destination = ListItemDestination.Action,
                trailingContent = {},
            )
        is ServiceRow.Open ->
            when (val target = row.target) {
                is OpenTarget.App ->
                    externalItem(Icons.Filled.PlayArrow, stringResource(R.string.open_in_named, target.app.name)) {
                        context.openServiceApp(target)
                    }
                is OpenTarget.Web ->
                    externalItem(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_in_named, serviceName)) {
                        context.openInBrowser(target.url)
                    }
            }
    }

private const val PERCENT = 100
