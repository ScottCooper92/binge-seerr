package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
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
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeSheetFooter
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeSheetNav
import com.binge.designsystem.component.bingeSheetScroll
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.setGroups
import java.time.LocalDate
import com.binge.designsystem.R as DesR

/**
 * The grid's filter sheet, as Binge's discover filter sheet is: the kind of title and the order at the top as single-choice
 * chips, then a row for each filter that opens its own page over the sheet. The user edits a draft, and Show results applies
 * the kind, the order and the filters at once.
 */
@Composable
internal fun DiscoverFilterSheet(
    kind: TitleKind,
    sort: DiscoverSort,
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    today: LocalDate,
    loads: FilterLoads,
    onApply: (TitleKind, DiscoverSort, DiscoverFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = DiscoverFilterDraft.Saver) { mutableStateOf(DiscoverFilterDraft(kind, sort, filters)) }
    var choosing by rememberSaveable { mutableStateOf<FilterChoosing?>(null) }
    val words = if (draft.kind == kind) vocabulary else vocabulary.forOtherKind()
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
            RowsPage(draft, words, today, nav, { choosing = FilterChoosing.Language }, { draft = it }) {
                onApply(draft.kind, draft.sort, draft.filters)
                onDismiss()
            }
        } else {
            DiscoverSectionPage(
                section = section,
                filters = draft.filters,
                vocabulary = words,
                today = today,
                loads = loads,
                onOpenRegion = { choosing = FilterChoosing.Region },
                onChange = { draft = draft.copy(filters = it) },
                onDone = nav::pop,
            )
        }
    }
    choosing?.let { FilterChoiceSheet(it, draft.filters, words, { draft = draft.copy(filters = it) }) { choosing = null } }
}

@Composable
private fun ColumnScope.RowsPage(
    draft: DiscoverFilterDraft,
    vocabulary: FilterVocabulary,
    today: LocalDate,
    nav: BingeSheetNav,
    onChooseLanguage: () -> Unit,
    onChange: (DiscoverFilterDraft) -> Unit,
    onApply: () -> Unit,
) {
    Column(Modifier.weight(1f, fill = false).bingeSheetScroll()) {
        DiscoverFilterSheetContent(
            draft = draft,
            vocabulary = vocabulary,
            today = today,
            onKind = { onChange(draft.withKind(it)) },
            onSort = { onChange(draft.withSort(it)) },
            onOpen = { if (it == DiscoverFilterSection.Language) onChooseLanguage() else nav.push(it.name) },
            onClearAll = { onChange(draft.cleared()) },
        )
    }
    BingeSheetFooter {
        BingeActionFooter(label = stringResource(R.string.grid_filters_show_results), onClick = onApply)
    }
}

/** What the rows page shows, apart from the window it sits in, so a frame can draw it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverFilterSheetContent(
    draft: DiscoverFilterDraft,
    vocabulary: FilterVocabulary,
    today: LocalDate,
    onKind: (TitleKind) -> Unit,
    onSort: (DiscoverSort) -> Unit,
    onOpen: (DiscoverFilterSection) -> Unit,
    onClearAll: () -> Unit,
) {
    val inset = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))
    val gap = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))
    Column(modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_m))) {
        FilterSheetHeader(appliedCount = draft.filters.setGroups().size, canClear = !draft.isDefault, onClearAll = onClearAll)
        SectionTitle(stringResource(R.string.grid_filters_content))
        FlowRow(modifier = inset, horizontalArrangement = gap) {
            listOf(TitleKind.Movie, TitleKind.Series).forEach { kind ->
                BingeFilterChip(
                    label = stringResource(kind.contentLabel()),
                    selected = kind == draft.kind,
                    onClick = { onKind(kind) },
                    leadingIcon = if (kind == TitleKind.Series) Icons.Filled.Tv else Icons.Filled.Movie,
                )
            }
        }
        SectionTitle(stringResource(R.string.grid_filters_sort))
        FlowRow(modifier = inset, horizontalArrangement = gap) {
            DiscoverSort.sheetChoices(draft.kind).forEach { choice ->
                BingeFilterChip(
                    label = stringResource(choice.labelRes()),
                    selected = choice == draft.sort,
                    onClick = { onSort(choice) },
                    leadingIcon = choice.icon(),
                )
            }
        }
        SectionTitle(stringResource(R.string.grid_filters_filter_by))
        DiscoverFilterRows(draft.filters, draft.kind, vocabulary, today, onOpen)
    }
}

private fun TitleKind.contentLabel(): Int = if (this == TitleKind.Series) R.string.grid_content_tv else R.string.grid_content_movies

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

/** What the sheet asks its view model to load as a page opens or a keyword is typed. */
internal class FilterLoads(
    val onProviders: (String) -> Unit,
    val onKeywordQuery: (String) -> Unit,
    val onNameKeywords: (Set<Int>) -> Unit,
)

/**
 * The lists that belong to the kind the grid opened on are left out when the sheet is on the other: its genres, streaming
 * providers and ratings are another set. The lists both kinds share stay.
 */
private fun FilterVocabulary.forOtherKind() =
    copy(genres = emptyList(), regions = emptyList(), providers = emptyMap(), certifications = emptyMap())
