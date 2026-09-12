package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * A sign-in finishing elsewhere: the code to approve, and a wait. Dismissing is cancelling. The
 * Plex page opens itself once; the button is for a tab the user closed.
 */
@Composable
internal fun SetupLinkSheet(
    link: LinkFlow,
    onPlexLaunched: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    if (link is LinkFlow.Plex) {
        LaunchedEffect(link.launchPending) {
            if (link.launchPending) {
                context.openInBrowser(link.authUrl)
                onPlexLaunched()
            }
        }
    }
    BingeBottomSheet(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (link is LinkFlow.Plex) R.string.link_plex_title else R.string.link_quick_connect_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(if (link is LinkFlow.Plex) R.string.link_plex_body else R.string.link_quick_connect_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(link.code, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
            BingeLoadingIndicator()
            Text(stringResource(R.string.link_waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (link is LinkFlow.Plex) {
                BingeFilledButton(
                    label = stringResource(R.string.link_plex_open),
                    onClick = { context.openInBrowser(link.authUrl) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BingeOutlinedButton(label = stringResource(R.string.link_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
        }
    }
}
