package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.title.TitleRequest
import com.cooper.binge.companion.seerr.ui.requests.DetailDownload
import com.cooper.binge.companion.seerr.ui.requests.OpenTarget
import com.cooper.binge.companion.seerr.ui.requests.SeasonState
import com.cooper.binge.companion.seerr.ui.requests.WatchStats
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailViewModel
import com.cooper.binge.companion.seerr.ui.requests.ServiceItem
import com.cooper.binge.companion.seerr.ui.requests.ServiceKind
import com.cooper.binge.companion.seerr.ui.requests.canResolve
import com.cooper.binge.companion.seerr.ui.requests.mediaServerBaseUrl
import com.cooper.binge.companion.seerr.ui.requests.openServiceApp
import com.cooper.binge.companion.seerr.ui.requests.openTarget
import com.cooper.binge.companion.seerr.ui.requests.serviceApps
import com.cooper.binge.companion.seerr.ui.state.externalItem
import com.cooper.binge.companion.seerr.ui.state.formatFileSize
import com.cooper.binge.companion.seerr.ui.openInBrowser
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
    if (requestsOpen) TitleRequestsSheet(requests, actions.onPickRequest, actions.onCloseRequests)
    if (servicesOpen) TitleServicesSheet(servicesContent, mediaServerName, actions.onCloseServiceInfo)
}

/** One row per request on the title: who made it, which version, where it stands. A row opens that request's sheet. */
@Composable
internal fun TitleRequestsSheet(
    requests: List<TitleRequest>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        ItemGroup(
            title = stringResource(R.string.title_requests_sheet_title),
            rows =
                requests.sortedByDescending { it.id }.map { request ->
                    ListItem(
                        icon = Icons.Filled.Movie,
                        label =
                            stringResource(R.string.title_request_row, request.id) +
                                if (request.is4k) " · " + stringResource(R.string.settings_service_4k) else "",
                        detail = request.status?.toString(),
                        onClick = { onPick(request.id) },
                    )
                },
        )
    }
}

/** One line of a services group, before it is drawn: what the sheet shows and what decides whether the Services action shows. */
internal sealed interface ServiceRow {
    data class Destination(
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
    detail: RequestDetail?,
    kind: TitleKind,
    tmdbId: Int,
    resolves: (Intent) -> Boolean,
): List<ServiceGroup> {
    detail ?: return emptyList()
    val arrKind = if (kind == TitleKind.Series) ServiceKind.Sonarr else ServiceKind.Radarr
    val arrRows =
        listOfNotNull(
            detail.destination?.let { ServiceRow.Destination(listOfNotNull(it.serverName, it.profileName).joinToString(" · "), it.rootFolder) }
                ?.takeIf { it.label.isNotBlank() },
        ) +
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
            detail.media?.instances?.firstNotNullOfOrNull { it.watch }?.let { ServiceRow.Watch(it) },
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
    requestId: Int?,
    kind: TitleKind,
    tmdbId: Int,
): List<ServiceGroup> {
    requestId ?: return emptyList()
    val context = LocalContext.current
    val viewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(
            key = "request-detail-$requestId",
            creationCallback = { factory -> factory.create(requestId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = (state as? RequestDetailUiState.Ready)?.detail
    return remember(detail, kind, tmdbId) { servicesContent(detail, kind, tmdbId) { context.canResolve(it) } }
}

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
        is ServiceRow.Destination -> ListItem(icon = Icons.Filled.Dns, label = row.label, detail = row.folder)
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
            )
        is ServiceRow.Season ->
            ListItem(
                icon = Icons.Filled.Movie,
                label = row.season.name ?: stringResource(R.string.request_season_number, row.season.number),
                detail = row.season.status?.toString(),
            )
        is ServiceRow.Watch ->
            ListItem(
                icon = Icons.Filled.Visibility,
                label = stringResource(R.string.services_plays),
                detail =
                    "${row.stats.playCount} · 7d ${row.stats.playCount7Days} · 30d ${row.stats.playCount30Days}" +
                        row.stats.users.takeIf { it.isNotEmpty() }?.let { " · " + it.joinToString(", ") }.orEmpty(),
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
