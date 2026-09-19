package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.SortSheet
import io.github.scottcooper92.binge.seerr.ui.users.settings.rowLabelColor
import com.binge.designsystem.R as DesR

/**
 * The row form of [ChoicePicker]: one line naming the current choice, opening a sheet with the
 * full list rather than stacking a chip per option. A list a server supplies has no length or
 * label width this app controls, so it is the only shape whose height does not scale with the
 * connected server's config — see #336.
 *
 * [enabled] is what an editor page passes while it saves, same as on [ChoicePicker]: off, it dims
 * the row and blocks the tap that opens the sheet. An already-open sheet closes the moment a save
 * starts too, so a pick made mid-save cannot land in a draft the request has already gone out
 * from — the constraint the chip form carried and this one has to keep by a different route.
 */
@Composable
fun <T> ChoiceRow(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    if (choices.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(enabled = enabled, role = Role.Button) { open = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = rowLabelColor(enabled))
            Text(
                choices.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.settings_value_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = rowLabelColor(enabled, MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = rowLabelColor(enabled, MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
    if (open) {
        SortSheet(
            title = title,
            choices = choices.map { it.first },
            selected = selected,
            label = { id -> choices.first { it.first == id }.second },
            onSelect = onSelect,
            onDismiss = { open = false },
        )
    }
}
