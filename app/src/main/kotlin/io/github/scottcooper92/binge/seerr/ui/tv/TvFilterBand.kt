package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import io.github.scottcooper92.binge.seerr.R

/**
 * The band above a board: what to show, then how to order it, on one D-pad line.
 *
 * Both halves are [TvFilterBand] rather than a picker, because a board's sort is two mutually
 * exclusive values and an overlay to choose between two is a worse trade on a remote than one press
 * right. The sort half is labelled because two rows of identical pills would not say which is which.
 */
@Composable
internal fun <F, S> TvBoardBands(
    filters: List<Pair<F, String>>,
    selectedFilter: F,
    onFilterChange: (F) -> Unit,
    sorts: List<Pair<S, String>>,
    selectedSort: S,
    onSortChange: (S) -> Unit,
    modifier: Modifier = Modifier,
    initialFocusedLabel: String? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_filter_band_gap)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvFilterBand(
            filters = filters,
            selected = selectedFilter,
            onSelect = onFilterChange,
            initialFocusedLabel = initialFocusedLabel,
        )
        Text(
            text = stringResource(R.string.sort_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TvFilterBand(filters = sorts, selected = selectedSort, onSelect = onSortChange)
    }
}

/**
 * A row of mutually exclusive filters where OK is the commit: passing over one changes nothing, since
 * changing a filter replaces the list beneath. [initialFocusedLabel] seeds a pill for a preview.
 */
@Composable
internal fun <T> TvFilterBand(
    filters: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    initialFocusedLabel: String? = null,
    arrival: TvArrivalFocus? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_filter_band_gap)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEachIndexed { index, (key, label) ->
            val isSelected = key == selected
            TvChoicePill(
                label = label,
                selected = isSelected,
                onSelect = { if (!isSelected) onSelect(key) },
                initiallyFocused = label == initialFocusedLabel,
                modifier = if (index == 0 && arrival != null) Modifier.tvArrivalTarget(arrival) else Modifier,
            )
        }
    }
}

/** One filter: focus fills the pill amber, the chosen one carries the amber label at rest. */
@Composable
internal fun TvChoicePill(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyFocused: Boolean = false,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val resting = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            modifier
                .height(dimensionResource(R.dimen.tv_form_option_height))
                .clip(BingeShapes.Pill)
                .background(MaterialTheme.colorScheme.surface, BingeShapes.Pill)
                .tvFocusFill(isFocused = focused, shape = BingeShapes.Pill)
                .tvClickable(onFocusChanged = { focused = it }, onClick = onSelect)
                .padding(horizontal = dimensionResource(R.dimen.tv_form_option_padding_horizontal))
                .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = tvFocusContentColor(isFocused = focused, resting = resting),
            maxLines = 1,
        )
    }
}
