package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.ui.bingeAnswersTitleLink
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.openTitle
import com.cooper.binge.companion.seerr.ui.state.ActionSheetGroup
import com.cooper.binge.companion.seerr.ui.state.externalItem

/** One place the title can be opened. */
internal class RequestOpenLink(
    val label: String,
    val icon: ImageVector,
    val onOpen: () -> Unit,
)

/**
 * Where this title lives elsewhere: the title itself, the media server, and Radarr or Sonarr.
 *
 * Two of the three used to be here and on the page at once, rendered from the same string
 * resources in the manage-media sheet and in the page's own row of buttons. They have one home now, and
 * it is the bar, because opening a URL navigates and the sheet below the bar is for things that
 * change something.
 */
@Composable
internal fun rememberRequestOpenLinks(detail: RequestDetail): List<RequestOpenLink> {
    val context = LocalContext.current
    val resources = LocalResources.current
    return remember(context, resources, detail) {
        // The title row names where it goes: Binge where it answers, the server's own page where it doesn't.
        val titleLabel =
            if (context.bingeAnswersTitleLink(detail.item.mediaType, detail.item.tmdbId)) {
                resources.getString(R.string.request_open_binge)
            } else {
                resources.getString(R.string.open_in_named, detail.serverName)
            }
        listOfNotNull(
            RequestOpenLink(titleLabel, Icons.AutoMirrored.Filled.OpenInNew) {
                context.openTitle(detail.item.mediaType, detail.item.tmdbId, detail.webUrl)
            },
            detail.mediaServerUrl?.let { url ->
                val label =
                    detail.mediaServerName?.let { resources.getString(R.string.open_in_named, it) }
                        ?: resources.getString(R.string.request_open_media_server)
                RequestOpenLink(label, Icons.Filled.PlayArrow) { context.openInBrowser(url) }
            },
            detail.serviceUrl?.let { url ->
                val labelRes =
                    if (detail.item.mediaType == RequestMediaType.Tv) R.string.media_open_sonarr else R.string.media_open_radarr
                RequestOpenLink(resources.getString(labelRes), Icons.Filled.Dns) { context.openInBrowser(url) }
            },
        )
    }
}

/** The media server the Seerr server fronts, by name; null where it has none configured or one this app does not know. */
internal fun SeerrServerProfile.mediaServerName(): String? =
    when (mediaServer) {
        SeerrMediaServer.Plex -> "Plex"
        SeerrMediaServer.Jellyfin -> "Jellyfin"
        SeerrMediaServer.Emby -> "Emby"
        SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> null
    }

@Composable
internal fun RequestOpenSheet(
    links: List<RequestOpenLink>,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        RequestOpenSheetContent(
            links = links,
            onOpen = { link ->
                onDismiss()
                link.onOpen()
            },
        )
    }
}

@Composable
internal fun RequestOpenSheetContent(
    links: List<RequestOpenLink>,
    onOpen: (RequestOpenLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionSheetGroup(
        title = stringResource(R.string.request_open_elsewhere),
        rows = links.map { link -> externalItem(link.icon, link.label) { onOpen(link) } },
        modifier = modifier,
    )
}
