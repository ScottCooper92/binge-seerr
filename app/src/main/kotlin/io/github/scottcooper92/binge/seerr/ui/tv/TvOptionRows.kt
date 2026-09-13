package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.tv.R as TvR

/**
 * A titled set of mutually exclusive options, stacked so a long root-folder path has the row's width to
 * itself. **OK is the commit**: the chosen option carries a tick, and moving focus across the rows changes
 * nothing until the user presses, since a choice here reloads what the rows below it offer.
 *
 * [initialFocusedLabel] seeds one row as focused for a preview; production passes null. [arrival] makes the
 * first row the one the page lands on.
 */
@Composable
internal fun <T> TvOptionGroup(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    initialFocusedLabel: String? = null,
    arrival: TvArrivalFocus? = null,
) {
    if (choices.isEmpty()) return
    Column(
        modifier = modifier.width(dimensionResource(R.dimen.tv_form_field_width)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_form_option_gap)),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        choices.forEachIndexed { index, (key, label) ->
            val isSelected = key == selected
            TvOptionRow(
                label = label,
                selected = isSelected,
                // Guarded here so re-pressing the current option is a no-op rather than a second commit.
                onSelect = { if (!isSelected) onSelect(key) },
                initiallyFocused = label == initialFocusedLabel,
                modifier = if (index == 0 && arrival != null) Modifier.tvArrivalTarget(arrival) else Modifier,
            )
        }
    }
}

/**
 * One option. Focus fills the pill amber; being chosen adds the tick, and the two are independent — the
 * current value keeps its tick while focus passes over it. The tick takes the label's colour, so it
 * inverts with the fill rather than sitting amber on amber.
 */
@Composable
internal fun TvOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyFocused: Boolean = false,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val resting = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val contentColor = tvFocusContentColor(isFocused = focused, resting = resting)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.tv_form_option_height))
                .clip(BingeShapes.Pill)
                .background(MaterialTheme.colorScheme.surface, BingeShapes.Pill)
                .tvFocusFill(isFocused = focused, shape = BingeShapes.Pill)
                .tvClickable(onFocusChanged = { focused = it }, onClick = onSelect)
                .padding(horizontal = dimensionResource(R.dimen.tv_form_option_padding_horizontal))
                .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_form_option_gap)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(dimensionResource(TvR.dimen.tv_button_icon)),
            )
        }
    }
}
