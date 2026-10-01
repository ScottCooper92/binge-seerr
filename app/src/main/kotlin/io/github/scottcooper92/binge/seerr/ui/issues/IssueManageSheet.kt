package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import com.binge.designsystem.R as DesR

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
    Column(modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
        ManageRow(stringResource(R.string.open_in_named, serverName), onOpenWeb)
        onOpenMediaServer?.let {
            ManageRow(
                mediaServerName?.let { name -> stringResource(R.string.open_in_named, name) }
                    ?: stringResource(R.string.request_open_media_server),
                it,
            )
        }
        onOpenService?.let { ManageRow(stringResource(R.string.open_in_named, serviceName), it) }
    }
}

@Composable
private fun ManageRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(onClick = onClick)
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
