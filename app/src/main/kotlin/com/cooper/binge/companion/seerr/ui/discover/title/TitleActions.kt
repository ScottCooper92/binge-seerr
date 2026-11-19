package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.LabelledIconButton
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The actions under the stats: the primary on the left, there in every state and saying where the title stands, then a round
 * Trailer button that carries a label. Share is in the page's bar only. [onTrailer] is null where the server lists no trailer, and the
 * button is not drawn.
 */
@Composable
internal fun TitleActions(
    model: TitleActionsModel,
    onPrimary: () -> Unit,
    onTrailer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.Top,
    ) {
        PrimaryAction(model.primary, onPrimary, Modifier.weight(1f))
        onTrailer?.let { LabelledIconButton(Icons.Filled.PlayArrow, stringResource(R.string.title_page_action_trailer), it) }
    }
}

/**
 * Request, filled, where nothing is held; otherwise an outlined button that says the state, with its icon. It is drawn disabled
 * where there is nothing to open yet.
 */
@Composable
private fun PrimaryAction(
    primary: TitlePrimary,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val enabled = primary.tap != PrimaryTap.Nothing
    if (primary.state == TitleState.Request) {
        BingeFilledButton(
            label = stringResource(if (primary.fourKOnly) R.string.title_page_request_4k else R.string.title_page_request),
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            leadingIcon = Icons.Filled.Add,
            enabled = enabled,
        )
    } else {
        BingeOutlinedButton(
            label = stringResource(primary.state.labelRes()),
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            leadingIcon = primary.state.icon(),
            enabled = enabled,
        )
    }
}

private fun TitleState.labelRes() =
    when (this) {
        TitleState.Request -> R.string.title_page_request
        TitleState.Requested -> R.string.hubs_card_requested
        TitleState.Processing -> R.string.hubs_card_processing
        TitleState.PartiallyAvailable -> R.string.hubs_card_partial
        TitleState.Available -> R.string.hubs_card_available
        TitleState.Blocklisted -> R.string.hubs_card_blocklisted
    }

private fun TitleState.icon() =
    when (this) {
        TitleState.Request -> Icons.Filled.Add
        TitleState.Requested -> Icons.Filled.Schedule
        TitleState.Processing -> Icons.Filled.Sync
        TitleState.PartiallyAvailable -> Icons.Filled.Timelapse
        TitleState.Available -> Icons.Filled.CheckCircle
        TitleState.Blocklisted -> Icons.Filled.Block
    }
