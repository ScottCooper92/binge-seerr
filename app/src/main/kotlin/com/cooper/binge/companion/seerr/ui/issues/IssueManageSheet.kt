package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.cooper.binge.companion.seerr.seerr.isOpenableSite
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.state.ActionSheetGroup
import com.cooper.binge.companion.seerr.ui.state.externalItem
import com.cooper.binge.companion.seerr.core.ui.R as UiR

/**
 * The page's overflow: the issue on the server, the title on the media server, and in Sonarr or Radarr. A link to an
 * address that cannot be a real site has no row: one that opens nothing is worse than none. [hasOpenableLink] says
 * whether any row is left; the page leaves the button out when none is.
 */
internal fun IssueDetail.hasOpenableLink(): Boolean = listOfNotNull(webUrl, mediaServerUrl, serviceUrl).any(::isOpenableSite)

@Composable
internal fun IssueManageSheet(
    detail: IssueDetail,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        IssueManageContent(
            serverName = detail.serverName,
            mediaServerName = detail.mediaServerName,
            serviceName = if (detail.item.mediaType == RequestMediaType.Tv) "Sonarr" else "Radarr",
            webUrl = detail.webUrl,
            mediaServerUrl = detail.mediaServerUrl,
            serviceUrl = detail.serviceUrl,
            onOpen = { url ->
                onDismiss()
                context.openInBrowser(url)
            },
        )
    }
}

@Composable
internal fun IssueManageContent(
    serverName: String,
    mediaServerName: String?,
    serviceName: String,
    webUrl: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    mediaServerUrl: String? = null,
    serviceUrl: String? = null,
) {
    ActionSheetGroup(
        rows =
            listOfNotNull(
                externalItem(Icons.AutoMirrored.Filled.OpenInNew, stringResource(UiR.string.open_in_named, serverName)) {
                    onOpen(webUrl)
                }.takeIf { isOpenableSite(webUrl) },
                mediaServerUrl?.takeIf(::isOpenableSite)?.let { url ->
                    externalItem(
                        Icons.Filled.PlayArrow,
                        mediaServerName?.let { name -> stringResource(UiR.string.open_in_named, name) }
                            ?: stringResource(UiR.string.request_open_media_server),
                    ) { onOpen(url) }
                },
                serviceUrl?.takeIf(::isOpenableSite)?.let { url ->
                    externalItem(Icons.Filled.Dns, stringResource(UiR.string.open_in_named, serviceName)) { onOpen(url) }
                },
            ),
        modifier = modifier,
    )
}
