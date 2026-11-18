package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.FilterChipItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.binge.designsystem.R as DesR

/**
 * The grid's filter sheet, as Binge's discover filter sheet is: the order at the top as single-choice chips, then the filters.
 * A choice applies at once, so the sheet has no button of its own and closes with Back or a tap outside.
 */
@Composable
internal fun DiscoverFilterSheet(
    sort: DiscoverSort,
    sortChoices: List<DiscoverSort>,
    onSort: (DiscoverSort) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeAdaptiveSheet(
        kind = BingeSheetKind.Task,
        onDismissRequest = onDismiss,
        skipPartiallyExpanded = false,
        dockable = true,
        stackPagesWhenCompact = true,
    ) {
        DiscoverFilterSheetContent(sort, sortChoices, onSort)
    }
}

/** What the sheet shows, apart from the window it sits in, so a frame can draw it. */
@Composable
internal fun DiscoverFilterSheetContent(
    sort: DiscoverSort,
    sortChoices: List<DiscoverSort>,
    onSort: (DiscoverSort) -> Unit,
) {
    val items = sortChoices.map { FilterChipItem(label = stringResource(it.labelRes()), icon = it.icon()) }
    Column(modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_m))) {
        Text(
            text = stringResource(R.string.grid_filters_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
        Text(
            text = stringResource(R.string.grid_filters_sort),
            style = MaterialTheme.typography.titleSmall,
            modifier =
                Modifier.padding(
                    start = dimensionResource(DesR.dimen.padding_m),
                    top = dimensionResource(DesR.dimen.padding_m),
                    end = dimensionResource(DesR.dimen.padding_m),
                    bottom = dimensionResource(DesR.dimen.padding_s),
                ),
        )
        BingeFilterChipRow(
            items = items,
            selectedIndex = sortChoices.indexOf(sort),
            onSelect = { onSort(sortChoices[it]) },
        )
    }
}
