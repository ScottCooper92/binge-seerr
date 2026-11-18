package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import coil3.compose.SubcomposeAsyncImage
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.BingeSheetFooter
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.bingeSheetScroll
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.binge.designsystem.R as DesR

/**
 * The streaming services of the region, in the order the title page lists them with their logos, to tick on and off. A row on
 * top opens the region's own page, and a search narrows the list.
 */
@Composable
internal fun WherePage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    loads: FilterLoads,
    onOpenRegion: () -> Unit,
    onChange: (DiscoverFilters) -> Unit,
) {
    val region = filters.region ?: vocabulary.defaultRegion
    LaunchedEffect(region) { region?.let(loads.onProviders) }
    val regionName = vocabulary.regions.firstOrNull { it.id == region }?.name ?: stringResource(R.string.grid_filter_any)
    var query by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = chipGap) {
        ListRow(onClick = onOpenRegion, verticalAlignment = Alignment.CenterVertically, modifier = inset) { modifier ->
            Column(modifier) {
                Text(stringResource(R.string.grid_filter_region), style = MaterialTheme.typography.labelMedium)
                Text(regionName, style = MaterialTheme.typography.titleMedium)
            }
        }
        val providers = region?.let { vocabulary.providers[it] }.orEmpty()
        if (providers.isEmpty()) {
            Text(stringResource(R.string.grid_filter_providers_none), style = MaterialTheme.typography.bodyMedium, modifier = inset)
        } else {
            SearchField(query, { query = it }, stringResource(R.string.grid_filter_providers_search), inset)
            val shown = providers.matching(query)
            if (shown.isEmpty()) NothingMatches(inset)
            shown.forEach { provider ->
                val id = provider.id.toInt()
                ProviderRow(
                    provider = provider,
                    selected = id in filters.providers,
                    onClick = { onChange(filters.copy(providers = filters.providers.toggled(id), region = region)) },
                    modifier = inset,
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: FilterOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        selected = selected,
        modifier = modifier,
        leading = {
            SubcomposeAsyncImage(
                model = provider.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(dimensionResource(R.dimen.filter_provider_logo_size))
                        .clip(RoundedCornerShape(BingeShapes.Medium.topStart))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        trailingContent = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
    ) { contentModifier -> Text(provider.name, style = MaterialTheme.typography.titleMedium, modifier = contentModifier) }
}

/**
 * The countries that have streaming providers, the chosen one first and ticked, with a search; choosing another clears the
 * services, as their ids differ by region.
 */
@Composable
internal fun RegionPage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    onChange: (DiscoverFilters) -> Unit,
) {
    val current = filters.region ?: vocabulary.defaultRegion
    var query by rememberSaveable { mutableStateOf("") }
    val inset = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))
    Column(verticalArrangement = chipGap) {
        SearchField(query, { query = it }, stringResource(R.string.grid_filter_region_search), inset)
        val shown = vocabulary.regions.matching(query).sortedByDescending { it.id == current }
        if (shown.isEmpty()) NothingMatches(inset)
        shown.forEach { region ->
            val selected = region.id == current
            ListRow(
                onClick = { if (!selected) onChange(filters.copy(region = region.id, providers = emptySet())) },
                verticalAlignment = Alignment.CenterVertically,
                selected = selected,
                modifier = inset,
                leading = { Icon(Icons.Filled.Public, contentDescription = null) },
                trailingContent = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
            ) { modifier -> Text(region.name, style = MaterialTheme.typography.titleMedium, modifier = modifier) }
        }
    }
}

/** The page the Where to watch page opens for its region, under the sheet's own bar once it docks, with a Done that goes back. */
@Composable
internal fun ColumnScope.RegionSheetPage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    onChange: (DiscoverFilters) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f, fill = false).bingeSheetScroll(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        SectionPageHeader(stringResource(R.string.grid_filter_region))
        RegionPage(filters, vocabulary, onChange)
    }
    BingeSheetFooter { BingeActionFooter(label = stringResource(R.string.grid_filters_done), onClick = onDone) }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
) {
    BingeSearchField(
        query = query,
        onQueryChange = onQueryChange,
        onClear = { onQueryChange("") },
        placeholder = placeholder,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun NothingMatches(modifier: Modifier) {
    Text(stringResource(R.string.grid_filter_search_none), style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

/** The options whose name has [query] in it, any case; all of them for a blank query. */
internal fun List<FilterOption>.matching(query: String): List<FilterOption> =
    if (query.isBlank()) this else filter { it.name.contains(query.trim(), ignoreCase = true) }
