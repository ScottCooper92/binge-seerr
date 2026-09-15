package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * The pinned bar: a field that opens the composer, and Resolve or Reopen, each only where the user
 * may. A status change in flight spins its button and holds the field, so the two cannot race.
 */
@Composable
internal fun ActionBar(
    detail: IssueDetail,
    action: IssueAction,
    onAddComment: () -> Unit,
    onToggleStatus: () -> Unit,
    inset: Dp,
    modifier: Modifier = Modifier,
) {
    val idle = action == IssueAction.None
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = inset, vertical = dimensionResource(DesR.dimen.padding_s)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_gap)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (detail.canComment) ComposerField(onClick = onAddComment, enabled = idle, modifier = Modifier.weight(1f))
            if (detail.canResolve) {
                val resolving = detail.item.status == IssueStatus.Open
                BingeOutlinedButton(
                    label = stringResource(if (resolving) R.string.issue_action_resolve else R.string.issue_action_reopen),
                    onClick = onToggleStatus,
                    enabled = idle,
                    loading = action == IssueAction.UpdatingStatus,
                    modifier = if (detail.canComment) Modifier else Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun ComposerField(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(BingeShapes.Pill)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(dimensionResource(DesR.dimen.hairline_thickness), MaterialTheme.colorScheme.outlineVariant, BingeShapes.Pill)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            text = stringResource(R.string.issue_comment_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
