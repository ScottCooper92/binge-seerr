package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusIndicator
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.BingeStatus
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * Binge's relationship to this device on the television hub, the counterpart of the phone's tile (#475).
 *
 * Only a missing Binge is something to act on, so only that state is a card the remote can focus:
 * select opens the Play Store listing. The other two are hints, plain text that stays out of the
 * focus order, the same as on the phone: installing or connecting retires the message by itself.
 *
 * [initiallyFocused] seeds the focus ring for a frame, as it does on the other TV components.
 */
@Composable
internal fun TvBingeTile(
    status: BingeStatus,
    onOpenPlayStore: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyFocused: Boolean = false,
) {
    when (status) {
        BingeStatus.NotInstalled -> TvBingeInstallCard(onOpenPlayStore, modifier, initiallyFocused)
        BingeStatus.NotConnected -> TvBingeHint(stringResource(R.string.connected_hint), modifier)
        BingeStatus.Connected -> TvBingeHint(stringResource(R.string.hub_binge_connected_hint), modifier)
    }
}

@Composable
private fun TvBingeHint(
    text: String,
    modifier: Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun TvBingeInstallCard(
    onOpenPlayStore: () -> Unit,
    modifier: Modifier,
    initiallyFocused: Boolean,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val shape = BingeShapes.AccountCard
    // One line, because the board has no height to spare: the hint it replaces was one line too.
    Row(
        modifier =
            modifier
                .tvFocusIndicator(isFocused = focused, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(dimensionResource(TvR.dimen.tv_button_border_width), MaterialTheme.colorScheme.border, shape)
                .tvClickable(onFocusChanged = { focused = it }, onClick = onOpenPlayStore)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_xs)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.hub_binge_not_installed_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.hub_binge_not_installed_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
