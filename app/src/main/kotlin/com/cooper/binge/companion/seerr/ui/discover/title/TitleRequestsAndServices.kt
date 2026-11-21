package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.cooper.binge.companion.seerr.ui.requests.OpenTarget
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
import com.cooper.binge.companion.seerr.ui.state.formatFileSize
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.binge.designsystem.R as DesR

/** The two sheets over a title page besides the request's own: the list of its requests, and the services it sits on. */
@Composable
internal fun TitleRequestsAndServices(
    requestsOpen: Boolean,
    servicesOpen: Boolean,
    requests: List<TitleRequest>,
    tmdbId: Int,
    kind: TitleKind,
    actions: TitlePageActions,
) {
    if (requestsOpen) TitleRequestsSheet(requests, actions.onPickRequest, actions.onCloseRequests)
    if (servicesOpen) requests.toManage()?.let { TitleServicesSheet(it.id, tmdbId, kind, actions.onCloseServiceInfo) }
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

/**
 * The services behind the title, read from the newest request's own page: Radarr or Sonarr with where it was sent, what is downloading
 * and the seasons, and the media server with its watch stats, each with the Open that suits it.
 */
@Composable
internal fun TitleServicesSheet(
    requestId: Int,
    tmdbId: Int,
    kind: TitleKind,
    onDismiss: () -> Unit,
) {
    val viewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(
            key = "request-detail-$requestId",
            creationCallback = { factory -> factory.create(requestId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = (state as? RequestDetailUiState.Ready)?.detail
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
            if (detail == null) {
                ItemGroup(title = stringResource(R.string.title_page_action_services), rows = emptyList())
            } else {
                ArrServiceGroup(detail, kind)
                MediaServerGroup(detail, tmdbId, kind)
            }
        }
    }
}

@Composable
private fun ArrServiceGroup(
    detail: RequestDetail,
    kind: TitleKind,
) {
    val context = LocalContext.current
    val isTv = kind == TitleKind.Series
    val name = stringResource(if (isTv) R.string.media_client_sonarr else R.string.media_client_radarr)
    val rows = mutableListOf<ListItem>()
    detail.destination?.let { destination ->
        rows += ListItem(
            icon = Icons.Filled.Dns,
            label = listOfNotNull(destination.serverName, destination.profileName).joinToString(" · ").ifBlank { name },
            detail = destination.rootFolder,
        )
    }
    detail.downloads.forEach { download ->
        rows += ListItem(
            icon = Icons.Filled.Download,
            label = download.title ?: stringResource(R.string.request_downloads),
            detail =
                listOfNotNull(
                    "${(download.fraction * PERCENT).toInt()}%",
                    download.totalBytes?.let(::formatFileSize),
                    download.etaMinutes?.let { "~$it min" },
                ).joinToString(" · "),
        )
    }
    detail.seasons.forEach { season ->
        rows += ListItem(
            icon = Icons.Filled.Movie,
            label = season.name ?: stringResource(R.string.request_season_number, season.number),
            detail = season.status?.toString(),
        )
    }
    val target = (if (isTv) ServiceKind.Sonarr else ServiceKind.Radarr).openTarget(ServiceItem(), detail.serviceUrl, serviceApps()) { false }
    (target as? OpenTarget.Web)?.let { web ->
        rows += ListItem(icon = Icons.AutoMirrored.Filled.OpenInNew, label = stringResource(R.string.open_in_named, name), onClick = { context.openInBrowser(web.url) })
    }
    if (rows.isNotEmpty()) ItemGroup(title = name, rows = rows)
}

@Composable
private fun MediaServerGroup(
    detail: RequestDetail,
    tmdbId: Int,
    kind: TitleKind,
) {
    val context = LocalContext.current
    val serverName = detail.mediaServerName ?: return
    val rows = mutableListOf<ListItem>()
    detail.media?.instances?.firstNotNullOfOrNull { it.watch }?.let { watch ->
        rows += ListItem(
            icon = Icons.Filled.Visibility,
            label = stringResource(R.string.services_plays),
            detail = "${watch.playCount} · 7d ${watch.playCount7Days} · 30d ${watch.playCount30Days}",
        )
        if (watch.users.isNotEmpty()) {
            rows += ListItem(icon = Icons.Filled.Group, label = stringResource(R.string.services_watched_by), detail = watch.users.joinToString(", "))
        }
    }
    val item =
        ServiceItem(
            serverUrl = mediaServerBaseUrl(detail.mediaServerUrl),
            itemId = detail.mediaServerItem?.itemId,
            tmdbType = if (kind == TitleKind.Series) "tv" else "movie",
            tmdbId = tmdbId,
        )
    val target = ServiceKind.Jellyfin.openTarget(item, detail.mediaServerUrl, serviceApps()) { context.canResolve(it) }
    when (target) {
        is OpenTarget.App ->
            rows += ListItem(icon = Icons.Filled.PlayArrow, label = stringResource(R.string.open_in_named, target.app.name), onClick = { context.openServiceApp(target) })
        is OpenTarget.Web ->
            rows += ListItem(icon = Icons.AutoMirrored.Filled.OpenInNew, label = stringResource(R.string.open_in_named, serverName), onClick = { context.openInBrowser(target.url) })
        null -> Unit
    }
    if (rows.isNotEmpty()) ItemGroup(title = serverName, rows = rows)
}

private const val PERCENT = 100
