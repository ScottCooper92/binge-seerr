package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.openTitle
import com.binge.designsystem.R as DesR

/** One place the title can be opened. */
internal class RequestOpenLink(
    @StringRes val labelRes: Int,
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
    return listOfNotNull(
        // The title itself hands off to Binge where it is installed; the rest are the server's own links.
        RequestOpenLink(R.string.request_open_title, Icons.AutoMirrored.Filled.OpenInNew) {
            context.openTitle(detail.item.mediaType, detail.item.tmdbId, detail.webUrl)
        },
        detail.mediaServerUrl?.let { url ->
            RequestOpenLink(R.string.request_open_media_server, Icons.Filled.PlayArrow) { context.openInBrowser(url) }
        },
        detail.serviceUrl?.let { url ->
            val labelRes =
                if (detail.item.mediaType == RequestMediaType.Tv) R.string.media_open_sonarr else R.string.media_open_radarr
            RequestOpenLink(labelRes, Icons.Filled.Dns) { context.openInBrowser(url) }
        },
    )
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
    Column(modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
        Text(
            text = stringResource(R.string.request_open_elsewhere),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
        )
        links.forEach { link ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                        .clickable { onOpen(link) }
                        .padding(
                            horizontal = dimensionResource(DesR.dimen.padding_m),
                            vertical = dimensionResource(DesR.dimen.padding_s),
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            ) {
                Icon(link.icon, contentDescription = null)
                Text(stringResource(link.labelRes), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
