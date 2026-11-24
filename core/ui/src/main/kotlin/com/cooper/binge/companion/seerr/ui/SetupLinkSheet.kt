package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSheetKind
import com.cooper.binge.companion.seerr.core.ui.R
import com.binge.designsystem.R as DesR

/**
 * A sign-in finishing elsewhere: the code to approve, and a wait. Dismissing is cancelling. The
 * Plex page opens itself once; the button is for a tab the user closed.
 */
@Composable
fun SetupLinkSheet(
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
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onCancel) {
        SetupLinkContent(
            link = link,
            onOpenPlex = { (link as? LinkFlow.Plex)?.let { context.openInBrowser(it.authUrl) } },
            onCancel = onCancel,
        )
    }
}

/** [SetupLinkSheet]'s body without its window, for a frame; [onOpenPlex] is the button a Plex link offers. */
@Composable
fun SetupLinkContent(
    link: LinkFlow,
    onOpenPlex: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
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
        Text(
            stringResource(R.string.link_waiting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (link is LinkFlow.Plex) {
            val open = stringResource(R.string.link_plex_open)
            val description = stringResource(DesR.string.cd_link_chip_opens_outside, open)
            // The page opens in the browser: the mark and the description say so (#2347).
            BingeFilledButton(
                label = open,
                onClick = onOpenPlex,
                leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            )
        }
        BingeOutlinedButton(label = stringResource(R.string.link_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}
