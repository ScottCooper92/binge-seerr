package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeStatusButton
import com.binge.designsystem.component.BingeStatusButtonEmphasis
import com.binge.designsystem.component.LabelledIconButton
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The actions under the stats: the primary on the left, there in every state and saying where the title stands, then a round
 * Trailer button and, once the title is in the library, a Report button, each carrying a label. Share is in the page's bar only. [onTrailer] is null where the server lists no trailer, and the
 * button is not drawn.
 */
@Composable
internal fun TitleActions(
    model: TitleActionsModel,
    onPrimary: () -> Unit,
    onTrailer: (() -> Unit)?,
    onReport: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.Top,
    ) {
        PrimaryAction(model.primary, onPrimary, Modifier.weight(1f))
        onTrailer?.let { LabelledIconButton(Icons.Filled.PlayArrow, stringResource(R.string.title_page_action_trailer), it) }
        onReport
            ?.takeIf {
                model.canReport
            }?.let { LabelledIconButton(Icons.Filled.ReportProblem, stringResource(R.string.title_page_action_report), it) }
    }
}

/**
 * The state as the design system's status button: Request, filled with its icon, where nothing is held, and otherwise the state with
 * its status dot, tonal. It is enabled in every state, because a tap always opens a sheet.
 */
@Composable
private fun PrimaryAction(
    primary: TitlePrimary,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val label = stringResource(primary.label())
    val request = primary.state == TitleState.Request
    BingeStatusButton(
        label = label,
        onClick = onClick,
        sentiment = primary.state.sentiment(),
        modifier = modifier.fillMaxWidth(),
        icon = if (request) Icons.Filled.Add else null,
        emphasis = if (request) BingeStatusButtonEmphasis.Filled else BingeStatusButtonEmphasis.Tonal,
    )
}

@StringRes
private fun TitlePrimary.label() = if (state == TitleState.Request && fourKOnly) R.string.title_page_request_4k else state.labelRes()

private fun TitleState.sentiment() =
    when (this) {
        TitleState.Request -> BingeSentiment.Neutral
        TitleState.Requested -> BingeSentiment.Caution
        TitleState.Processing, TitleState.PartiallyAvailable -> BingeSentiment.Info
        TitleState.Available -> BingeSentiment.Positive
        TitleState.Blocklisted -> BingeSentiment.Negative
    }

internal fun TitleState.labelRes() =
    when (this) {
        TitleState.Request -> R.string.title_page_request
        TitleState.Requested -> R.string.hubs_card_requested
        TitleState.Processing -> R.string.hubs_card_processing
        TitleState.PartiallyAvailable -> R.string.hubs_card_partial
        TitleState.Available -> R.string.hubs_card_available
        TitleState.Blocklisted -> R.string.hubs_card_blocklisted
    }
