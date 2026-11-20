package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.flags.bingePromotion
import com.cooper.binge.companion.seerr.seerr.isOpenableSite
import com.cooper.binge.companion.seerr.ui.bingeAnswersTitleLink
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.openTitle
import com.cooper.binge.companion.seerr.ui.state.ActionSheetGroup
import com.cooper.binge.companion.seerr.ui.state.externalItem
import com.cooper.binge.companion.seerr.ui.state.openItem
import com.cooper.binge.companion.seerr.ui.toTitleKind

/** One place the title can be opened. */
internal class RequestOpenLink(
    val label: String,
    val icon: ImageVector,
    /** Whether the row stays in the app, which the sheet shows with the chevron; a row that leaves it shows the external icon. */
    val inApp: Boolean = false,
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
    val promoted = LocalFlags.current.bingePromotion
    val openTitle = LocalOpenTitle.current
    val discovery = LocalDiscoveryEnabled.current
    return remember(context, resources, detail, promoted, openTitle, discovery) {
        // The title row names where it goes: Binge where it answers, the server's own page where it doesn't.
        val titleLabel =
            if (context.bingeAnswersTitleLink(promoted, detail.item.mediaType, detail.item.tmdbId)) {
                resources.getString(R.string.request_open_binge)
            } else {
                resources.getString(R.string.open_in_named, detail.serverName)
            }
        // A link to an address that cannot be a real site is left out: a row that opens nothing is worse than none.
        listOfNotNull(
            // With discovery on the title has a page here, which opens first even where the server's address is not a real site.
            RequestOpenLink(resources.getString(R.string.request_open_title), Icons.Filled.Movie, inApp = true) {
                openTitle(detail.item.mediaType.toTitleKind(), detail.item.tmdbId, detail.webUrl)
            }.takeIf { discovery },
            RequestOpenLink(titleLabel, Icons.AutoMirrored.Filled.OpenInNew) {
                context.openTitle(promoted, detail.item.mediaType, detail.item.tmdbId, detail.webUrl)
            }.takeIf { isOpenableSite(detail.webUrl) },
            detail.mediaServerUrl?.takeIf(::isOpenableSite)?.let { url ->
                val label =
                    detail.mediaServerName?.let { resources.getString(R.string.open_in_named, it) }
                        ?: resources.getString(R.string.request_open_media_server)
                RequestOpenLink(label, Icons.Filled.PlayArrow) { context.openInBrowser(url) }
            },
            detail.serviceUrl?.takeIf(::isOpenableSite)?.let { url ->
                val labelRes =
                    if (detail.item.mediaType == RequestMediaType.Tv) R.string.media_open_sonarr else R.string.media_open_radarr
                RequestOpenLink(resources.getString(labelRes), Icons.Filled.Dns) { context.openInBrowser(url) }
            },
        )
    }
}

@Composable
internal fun RequestOpenSheet(
    links: List<RequestOpenLink>,
    onDismiss: () -> Unit,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
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
        title = stringResource(if (LocalDiscoveryEnabled.current) R.string.request_open else R.string.request_open_elsewhere),
        rows =
            links.map { link ->
                if (link.inApp) openItem(link.icon, link.label) { onOpen(link) } else externalItem(link.icon, link.label) { onOpen(link) }
            },
        modifier = modifier,
    )
}
