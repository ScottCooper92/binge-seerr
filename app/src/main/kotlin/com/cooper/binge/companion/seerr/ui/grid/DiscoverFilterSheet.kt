package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeSheetNav
import com.binge.designsystem.template.FilterControlUi
import com.binge.designsystem.template.FilterOptionUi
import com.binge.designsystem.template.FilterRowUi
import com.binge.designsystem.template.FilterSectionUi
import com.binge.designsystem.template.FilterSheetContent
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.setGroups
import java.time.LocalDate

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
    DiscoverFilterSheetContent(
        draft = draft,
        vocabulary = vocabulary,
        today = today,
        onKind = { onChange(draft.withKind(it)) },
        onSort = { onChange(draft.withSort(it)) },
        onOpen = { if (it == DiscoverFilterSection.Language) onChooseLanguage() else nav.push(it.name) },
        onClearAll = { onChange(draft.cleared()) },
        onApply = onApply,
    )
}

/**
 * What the rows page shows, apart from the window it sits in, so a frame can draw it: the design system's filter sheet body,
 * with the content kind and the order as its choice sections and a row for each filter that opens a page of its own.
 */
@Composable
internal fun ColumnScope.DiscoverFilterSheetContent(
    draft: DiscoverFilterDraft,
    vocabulary: FilterVocabulary,
    today: LocalDate,
    onKind: (TitleKind) -> Unit,
    onSort: (DiscoverSort) -> Unit,
    onOpen: (DiscoverFilterSection) -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
) {
    val appliedCount = draft.filters.setGroups().size
    val sections =
        listOf(
            FilterSectionUi(
                id = KIND_SECTION,
                title = stringResource(R.string.grid_filters_content),
                control =
                    FilterControlUi.SingleChoice(
                        options =
                            listOf(TitleKind.Movie, TitleKind.Series).map { kind ->
                                FilterOptionUi(
                                    id = kind.name,
                                    label = stringResource(kind.contentLabel()),
                                    icon = if (kind == TitleKind.Series) Icons.Filled.Tv else Icons.Filled.Movie,
                                )
                            },
                        selectedId = draft.kind.name,
                    ),
            ),
            FilterSectionUi(
                id = SORT_SECTION,
                title = stringResource(R.string.grid_filters_sort),
                control =
                    FilterControlUi.SingleChoice(
                        options =
                            DiscoverSort.sheetChoices(draft.kind).map { choice ->
                                FilterOptionUi(choice.name, stringResource(choice.labelRes()), choice.icon())
                            },
                        selectedId = draft.sort.name,
                    ),
            ),
        )
    val offered = DiscoverFilterSection.entries.filter { it.isOffered(vocabulary, draft.kind) }
    val rows =
        offered.map { section ->
            FilterRowUi(
                id = section.name,
                title = stringResource(section.title),
                summary = section.summary(draft.filters, vocabulary, today),
                icon = section.icon,
            )
        }
    FilterSheetContent(
        title = stringResource(R.string.grid_filters_title),
        clearAllLabel = stringResource(R.string.grid_filters_clear_all),
        showResultsLabel = stringResource(R.string.grid_filters_show_results),
        sections = sections,
        onApply = onApply,
        onClearAll = onClearAll,
        appliedLabel = if (appliedCount > 0) pluralStringResource(R.plurals.grid_filters_applied, appliedCount, appliedCount) else null,
        rowsTitle = stringResource(R.string.grid_filters_filter_by),
        rows = rows,
        onSingleSelect = { section, option ->
            when (section) {
                KIND_SECTION -> onKind(TitleKind.valueOf(option))
                SORT_SECTION -> onSort(DiscoverSort.valueOf(option))
            }
        },
        onRowClick = { id -> onOpen(DiscoverFilterSection.valueOf(id)) },
        clearAllEnabled = !draft.isDefault,
    )
}

private const val KIND_SECTION = "kind"
private const val SORT_SECTION = "sort"

private fun TitleKind.contentLabel(): Int = if (this == TitleKind.Series) R.string.grid_content_tv else R.string.grid_content_movies

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
