package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSheetFooter
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeSheetNav
import com.binge.designsystem.component.FilterChipItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import java.time.LocalDate
import com.binge.designsystem.R as DesR

/**
 * The grid's filter sheet, as Binge's discover filter sheet is: the order at the top as single-choice chips, then a row for each
 * filter that opens its own page over the sheet. The user edits a draft, and Show results applies the order and the filters at once.
 */
@Composable
internal fun DiscoverFilterSheet(
    sort: DiscoverSort,
    filters: DiscoverFilters,
    sortChoices: List<DiscoverSort>,
    today: LocalDate,
    onApply: (DiscoverSort, DiscoverFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = DiscoverFilterDraft.Saver) { mutableStateOf(DiscoverFilterDraft(sort, filters)) }
    BingeAdaptiveSheet(
        kind = BingeSheetKind.Task,
        onDismissRequest = onDismiss,
        skipPartiallyExpanded = false,
        dockable = true,
        stackPagesWhenCompact = true,
        backLabel = { null },
    ) { nav ->
        val section = DiscoverFilterSection.entries.firstOrNull { it.name == nav.page }
        if (section == null) {
            RowsPage(draft, sortChoices, today, nav, { draft = it }) {
                onApply(draft.sort, draft.filters)
                onDismiss()
            }
        } else {
            DiscoverSectionPage(section, draft.filters, today, onChange = { draft = draft.copy(filters = it) }, onDone = nav::pop)
        }
    }
}

@Composable
private fun ColumnScope.RowsPage(
    draft: DiscoverFilterDraft,
    sortChoices: List<DiscoverSort>,
    today: LocalDate,
    nav: BingeSheetNav,
    onChange: (DiscoverFilterDraft) -> Unit,
    onApply: () -> Unit,
) {
    DiscoverFilterSheetContent(
        draft = draft,
        sortChoices = sortChoices,
        today = today,
        onSort = { onChange(draft.copy(sort = it)) },
        onOpen = { nav.push(it.name) },
        onClearAll = { onChange(draft.copy(filters = DiscoverFilters())) },
    )
    BingeSheetFooter {
        BingeActionFooter(label = stringResource(R.string.grid_filters_show_results), onClick = onApply)
    }
}

/** What the rows page shows, apart from the window it sits in, so a frame can draw it. */
@Composable
internal fun DiscoverFilterSheetContent(
    draft: DiscoverFilterDraft,
    sortChoices: List<DiscoverSort>,
    today: LocalDate,
    onSort: (DiscoverSort) -> Unit,
    onOpen: (DiscoverFilterSection) -> Unit,
    onClearAll: () -> Unit,
) {
    val items = sortChoices.map { FilterChipItem(label = stringResource(it.labelRes()), icon = it.icon()) }
    Column(modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_m))) {
        FilterSheetHeader(showClear = !draft.filters.isEmpty, onClearAll = onClearAll)
        SectionTitle(stringResource(R.string.grid_filters_sort))
        BingeFilterChipRow(items = items, selectedIndex = sortChoices.indexOf(draft.sort), onSelect = { onSort(sortChoices[it]) })
        SectionTitle(stringResource(R.string.grid_filters_filter_by))
        DiscoverFilterRows(draft.filters, today, onOpen)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier =
            Modifier.padding(
                start = dimensionResource(DesR.dimen.padding_m),
                top = dimensionResource(DesR.dimen.padding_m),
                end = dimensionResource(DesR.dimen.padding_m),
                bottom = dimensionResource(DesR.dimen.padding_s),
            ),
    )
}
