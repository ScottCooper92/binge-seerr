package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.LinkFlow

/**
 * A sign-in that finishes somewhere else, on a television: the code to enter, and the wait, in the tab that asked for
 * it, as the code tab shows its own.
 *
 * The polling is the ViewModel's and is the same the phone drives, so this is only the code on screen. There is no
 * button: Back, or moving to another tab, abandons it.
 */
@Composable
internal fun ColumnScope.TvSetupLinkContent(link: LinkFlow) {
    Text(
        text = stringResource(link.bodyRes()),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(dimensionResource(R.dimen.tv_form_field_width)),
    )
    // Monospace so a remote-read code cannot be mistaken a character at a time across the room. Set in the page's own
    // colour: the default is dark, which is unreadable here.
    Text(
        text = link.code,
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        text = stringResource(R.string.link_waiting),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Plex's body is the television's own, not the phone sheet's: no browser opens here to hand the code to. */
private fun LinkFlow.bodyRes(): Int =
    when (this) {
        is LinkFlow.Plex -> R.string.tv_link_plex_body
        is LinkFlow.QuickConnect -> R.string.link_quick_connect_body
    }
