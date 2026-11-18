package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeDateBoundFields
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeNumberSlider
import com.binge.designsystem.component.BingeRangeSlider
import com.binge.designsystem.component.BingeSheetFooter
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.ListRow
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
        Text(text = stringResource(section.title), style = MaterialTheme.typography.titleLarge, modifier = inset)
        when (section) {
            DiscoverFilterSection.Genres -> GenresPage(filters, vocabulary, inset, onChange)
            DiscoverFilterSection.Language -> LanguagePage(filters, vocabulary, onChange)
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

/** The genres as chips to tap on and off, and whether a title needs all of them or any one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenresPage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    val gap = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))
    Column(modifier = inset, verticalArrangement = gap) {
        Text(stringResource(R.string.grid_filter_match), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = gap) {
            BingeFilterChip(
                label = stringResource(R.string.grid_filter_match_all),
                selected = !filters.genresAny,
                onClick = { onChange(filters.copy(genresAny = false)) },
            )
            BingeFilterChip(
                label = stringResource(R.string.grid_filter_match_any),
                selected = filters.genresAny,
                onClick = { onChange(filters.copy(genresAny = true)) },
            )
        }
        FlowRow(horizontalArrangement = gap) {
            vocabulary.genres.forEach { genre ->
                val id = genre.id.toInt()
                BingeFilterChip(
                    label = genre.name,
                    selected = id in filters.genres,
                    onClick = { onChange(filters.copy(genres = filters.genres.toggled(id))) },
                )
            }
        }
    }
}

internal fun Set<Int>.toggled(id: Int): Set<Int> = if (id in this) this - id else this + id

/** One original language, chosen from the list the server knows; choosing the one set again clears it. */
@Composable
private fun LanguagePage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    onChange: (DiscoverFilters) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        vocabulary.languages.forEach { language ->
            val selected = language.id == filters.language
            ListRow(
                onClick = { onChange(filters.copy(language = if (selected) null else language.id)) },
                verticalAlignment = Alignment.CenterVertically,
                selected = selected,
                trailingContent = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
            ) { modifier ->
                Text(language.name, style = MaterialTheme.typography.titleMedium, modifier = modifier)
            }
        }
    }
}
