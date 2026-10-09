package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * One result of a search that collects things: what it is, and whether it has been added. The whole row toggles it, and
 * the mark at the end says which way a tap goes, a plus to add or a tick once added. [supporting] is an optional second
 * line, a count or a kind, and [icon] an optional mark for what sort of thing the result is.
 */
@Composable
fun SearchResultRow(
    title: String,
    added: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(R.dimen.search_result_min_height))
                .toggleable(value = added, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        icon?.let {
            Box(
                modifier =
                    Modifier
                        .size(dimensionResource(R.dimen.search_result_icon_container))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        // A wider gap before the toggle than between the icon and the text, so a long title never crowds the mark.
        Column(modifier = Modifier.weight(1f).padding(end = dimensionResource(DesR.dimen.padding_s))) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            modifier =
                Modifier
                    .size(dimensionResource(R.dimen.search_result_toggle))
                    .background(
                        if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (added) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = null,
                tint = if (added) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
