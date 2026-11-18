package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.component.StatusChip
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The actions under the stats: the primary on the left, then round buttons that each carry a label. [onTrailer] is null
 * where the server lists no trailer, and the button is not drawn. A line under the row says where the other version stands.
 */
@Composable
internal fun TitleActions(
    model: TitleActionsModel,
    onRequest: () -> Unit,
    onTrailer: (() -> Unit)?,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            verticalAlignment = Alignment.Top,
        ) {
            PrimaryAction(model.primary, onRequest, Modifier.weight(1f))
            onTrailer?.let { LabelledRoundButton(Icons.Filled.PlayArrow, stringResource(R.string.title_page_action_trailer), it) }
            LabelledRoundButton(Icons.Filled.Share, stringResource(R.string.title_page_action_share), onShare)
        }
        model.otherStatuses.forEach { OtherStatusLine(it) }
    }
}

@Composable
private fun PrimaryAction(
    primary: TitlePrimary,
    onRequest: () -> Unit,
    modifier: Modifier,
) {
    when (primary) {
        is TitlePrimary.Request ->
            BingeFilledButton(
                label = stringResource(if (primary.fourKOnly) R.string.title_page_request_4k else R.string.title_page_request),
                onClick = onRequest,
                modifier = modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.Add,
            )
        is TitlePrimary.Status ->
            primary.status.label()?.let { (text, sentiment) ->
                StatusChip(text, sentiment, modifier = modifier, singleLine = false)
            }
        TitlePrimary.None -> Unit
    }
}

@Composable
private fun OtherStatusLine(status: VersionStatus) {
    val text = status.label()?.first ?: return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The version's state as a short phrase: "Requested · 4K". Null where there is nothing to say. */
@Composable
private fun VersionStatus.label() =
    availability.label()?.let { (text, sentiment) ->
        stringResource(if (fourK) R.string.title_page_status_4k else R.string.title_page_status_hd, text) to sentiment
    }

/** A tonal round button with its name under it, so an icon never has to be guessed at. */
@Composable
private fun LabelledRoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(min = dimensionResource(R.dimen.title_action_round_min_width)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExpressiveIconButton(onClick = onClick, icon = icon, contentDescription = label, tone = IconButtonTone.Tonal)
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}
