package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.state.ActionSheetGroup
import io.github.scottcooper92.binge.seerr.ui.state.externalItem

/** The page's overflow: the issue on the server, the title on the media server, and in Sonarr or Radarr. */
@Composable
internal fun IssueManageSheet(
    detail: IssueDetail,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    fun open(url: String): () -> Unit =
        {
            onDismiss()
            context.openInBrowser(url)
        }
    BingeBottomSheet(onDismissRequest = onDismiss) {
        IssueManageContent(
            serverName = detail.serverName,
            mediaServerName = detail.mediaServerName,
            serviceName = if (detail.item.mediaType == RequestMediaType.Tv) "Sonarr" else "Radarr",
            onOpenWeb = open(detail.webUrl),
            onOpenMediaServer = detail.mediaServerUrl?.let(::open),
            onOpenService = detail.serviceUrl?.let(::open),
        )
    }
}

@Composable
internal fun IssueManageContent(
    serverName: String,
    mediaServerName: String?,
    serviceName: String,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMediaServer: (() -> Unit)? = null,
    onOpenService: (() -> Unit)? = null,
) {
    ActionSheetGroup(
        rows =
            listOfNotNull(
                externalItem(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_in_named, serverName), onOpenWeb),
                onOpenMediaServer?.let {
                    externalItem(
                        Icons.Filled.PlayArrow,
                        mediaServerName?.let { name -> stringResource(R.string.open_in_named, name) }
                            ?: stringResource(R.string.request_open_media_server),
                        it,
                    )
                },
                onOpenService?.let { externalItem(Icons.Filled.Dns, stringResource(R.string.open_in_named, serviceName), it) },
            ),
        modifier = modifier,
    )
}
