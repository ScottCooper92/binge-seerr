package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeDateBoundFields
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeNumberSlider
import com.binge.designsystem.component.BingeRangeSlider
import com.binge.designsystem.component.BingeSheetDockingHeader
import com.binge.designsystem.component.BingeSheetFooter
import com.binge.designsystem.component.BingeSheetTopBar
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.CheckboxRow
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.bingeSheetScroll
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.ReleasePreset
import com.cooper.binge.companion.seerr.discover.grid.cleared
import com.cooper.binge.companion.seerr.discover.grid.withRelease
import java.text.NumberFormat
import java.time.LocalDate
import com.binge.designsystem.R as DesR

private const val RATING_MAX = 9
private const val RUNTIME_MAX = 300
private const val RUNTIME_STEP = 10
private const val VOTES_MAX = 5000
private const val VOTES_STEP = 250

/** The page a filter row opens over the sheet: its title with Clear, the control, and Done. */
@Composable
internal fun ColumnScope.DiscoverSectionPage(
    section: DiscoverFilterSection,
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    today: LocalDate,
    loads: FilterLoads,
    onOpenRegion: () -> Unit,
    onChange: (DiscoverFilters) -> Unit,
    onDone: () -> Unit,
) {
    val inset = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))
    Column(
        modifier = Modifier.weight(1f, fill = false).bingeSheetScroll(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        SectionPageHeader(stringResource(section.title))
        when (section) {
            DiscoverFilterSection.Genres -> GenresPage(filters, vocabulary, inset, onChange)
            DiscoverFilterSection.Language -> Unit
            DiscoverFilterSection.Release -> ReleasePage(filters, today, inset, onChange)
            DiscoverFilterSection.Rating -> RatingPage(filters, inset, onChange)
            DiscoverFilterSection.Runtime -> RuntimePage(filters, inset, onChange)
            DiscoverFilterSection.Votes -> VotesPage(filters, inset, onChange)
            DiscoverFilterSection.WhereToWatch -> WherePage(filters, vocabulary, inset, loads, onOpenRegion, onChange)
            DiscoverFilterSection.Certification -> CertificationPage(filters, vocabulary, inset, onChange)
            DiscoverFilterSection.Keywords ->
                KeywordsPage(filters.keywords, vocabulary, inset, loads) { onChange(filters.copy(keywords = it)) }
            DiscoverFilterSection.ExcludeKeywords ->
                KeywordsPage(filters.excludeKeywords, vocabulary, inset, loads) { onChange(filters.copy(excludeKeywords = it)) }
            DiscoverFilterSection.Status -> StatusPage(filters, inset, onChange)
        }
        BingeTextButton(
            label = stringResource(R.string.grid_filters_clear),
            onClick = { onChange(filters.cleared(section.group)) },
            modifier = inset,
        )
    }
    BingeSheetFooter { BingeActionFooter(label = stringResource(R.string.grid_filters_done), onClick = onDone) }
}

/**
 * A page's heading: the large title while the sheet floats and the sheet's own bar once it docks, so the title is on screen
 * once, whichever the sheet is doing. It carries the sheet's drag handle.
 */
@Composable
internal fun SectionPageHeader(title: String) {
    BingeSheetDockingHeader(
        header = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier.padding(
                        start = dimensionResource(DesR.dimen.padding_m),
                        top = dimensionResource(DesR.dimen.padding_m),
                        end = dimensionResource(DesR.dimen.padding_m),
                    ),
            )
        },
        dockedTopBar = { onClose -> BingeSheetTopBar(title = title, onClose = onClose) },
    )
}

@Composable
private fun ReleasePage(
    filters: DiscoverFilters,
    today: LocalDate,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    val presets = ReleasePreset.entries
    val selected = ReleasePreset.of(filters.releaseFrom, filters.releaseTo, today)
    BingeFilterChipRow(
        items = presets.map { FilterChipItem(label = stringResource(it.label())) },
        selectedIndex = presets.indexOf(selected),
        onSelect = { onChange(filters.withRelease(presets[it], today)) },
    )
    BingeDateBoundFields(
        from = filters.releaseFrom?.let(LocalDate::parse),
        to = filters.releaseTo?.let(LocalDate::parse),
        fromLabel = stringResource(R.string.grid_filter_from),
        toLabel = stringResource(R.string.grid_filter_to),
        fromClearDescription = stringResource(R.string.grid_filter_clear_from),
        toClearDescription = stringResource(R.string.grid_filter_clear_to),
        onFromChange = { onChange(filters.copy(releaseFrom = it?.toString())) },
        onToChange = { onChange(filters.copy(releaseTo = it?.toString())) },
        fromMaximum = filters.releaseTo?.let(LocalDate::parse),
        toMinimum = filters.releaseFrom?.let(LocalDate::parse),
        today = today,
        modifier = inset,
    )
}

@Composable
private fun RatingPage(
    filters: DiscoverFilters,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    BingeNumberSlider(
        value = filters.ratingMin?.toInt() ?: 0,
        onValueChange = { onChange(filters.copy(ratingMin = it?.takeIf { value -> value > 0 }?.toDouble())) },
        range = 0..RATING_MAX,
        format = { it.toString() },
        description = stringResource(R.string.grid_filter_rating),
        modifier = inset,
    )
}

@Composable
private fun RuntimePage(
    filters: DiscoverFilters,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    val resources = LocalContext.current.resources
    val start = (filters.runtimeMin ?: 0).toFloat()
    val end = (filters.runtimeMax ?: RUNTIME_MAX).toFloat()
    BingeRangeSlider(
        values = start..end,
        onValuesChange = {
            onChange(
                filters.copy(
                    runtimeMin = it.start.toInt().takeIf { value -> value > 0 },
                    runtimeMax = it.endInclusive.toInt().takeIf { value -> value < RUNTIME_MAX },
                ),
            )
        },
        valueRange = 0f..RUNTIME_MAX.toFloat(),
        steps = RUNTIME_MAX / RUNTIME_STEP - 1,
        valueLabel = { resources.getString(R.string.grid_filter_minutes, it.toInt()) },
        startThumbDescription = stringResource(R.string.grid_filter_runtime_start),
        endThumbDescription = stringResource(R.string.grid_filter_runtime_end),
        openEndLabel = stringResource(R.string.grid_filter_any),
        modifier = inset,
    )
}

@Composable
private fun VotesPage(
    filters: DiscoverFilters,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    val number = remember { NumberFormat.getInstance() }
    BingeNumberSlider(
        value = filters.voteCountMin ?: 0,
        onValueChange = { onChange(filters.copy(voteCountMin = it?.takeIf { value -> value > 0 })) },
        range = 0..VOTES_MAX,
        format = { number.format(it) },
        description = stringResource(R.string.grid_filter_votes),
        step = VOTES_STEP,
        modifier = inset,
    )
}

/** The genres as ticks, and whether a title needs all of them or any one. A long vocabulary is a list, not a cloud of chips. */
@Composable
private fun GenresPage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    val gap = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))
    Column(verticalArrangement = gap) {
        Text(stringResource(R.string.grid_filter_match), style = MaterialTheme.typography.titleSmall, modifier = inset)
        BingeFilterChipRow(
            items =
                listOf(
                    FilterChipItem(stringResource(R.string.grid_filter_match_all)),
                    FilterChipItem(stringResource(R.string.grid_filter_match_any)),
                ),
            selectedIndex = if (filters.genresAny) 1 else 0,
            onSelect = { onChange(filters.copy(genresAny = it == 1)) },
        )
        Column(modifier = inset) {
            vocabulary.genres.forEach { genre ->
                val id = genre.id.toInt()
                CheckboxRow(
                    label = genre.name,
                    checked = id in filters.genres,
                    onCheckedChange = { onChange(filters.copy(genres = filters.genres.toggled(id))) },
                    icon = DiscoverFilterSection.Genres.icon,
                )
            }
        }
    }
}

internal fun Set<Int>.toggled(id: Int): Set<Int> = if (id in this) this - id else this + id
