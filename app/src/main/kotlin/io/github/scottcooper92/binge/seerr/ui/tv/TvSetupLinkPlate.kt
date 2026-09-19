package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.LinkFlow

/**
 * A sign-in that finishes somewhere else, on a television: the code to enter, and the wait.
 *
 * The polling is the ViewModel's and is the same the phone drives, so this is only the code on
 * screen and a way out. Cancel takes the arrival focus because it is the one control here — a remote
 * that lands on nothing cannot leave the page.
 */
@Composable
internal fun TvSetupLinkPlate(
    link: LinkFlow,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvFormPage(
        headline = stringResource(link.titleRes()),
        body = stringResource(link.bodyRes()),
        icon = Icons.Filled.PhoneAndroid,
        modifier = modifier,
    ) {
        // Monospace so a remote-read code cannot be mistaken a character at a time across the room.
        Text(
            text = link.code,
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Monospace,
        )
        TvFormNote(stringResource(R.string.link_waiting))
        TvButton(
            label = stringResource(R.string.link_cancel),
            onClick = onCancel,
            style = TvButtonStyle.Primary,
            modifier = Modifier.tvArrivalTarget(arrival),
        )
    }
}

private fun LinkFlow.titleRes(): Int =
    when (this) {
        is LinkFlow.Plex -> R.string.link_plex_title
        is LinkFlow.QuickConnect -> R.string.link_quick_connect_title
    }

/** Plex's body is the television's own, not the phone sheet's: no browser opens here to hand the code to. */
private fun LinkFlow.bodyRes(): Int =
    when (this) {
        is LinkFlow.Plex -> R.string.tv_link_plex_body
        is LinkFlow.QuickConnect -> R.string.link_quick_connect_body
    }
