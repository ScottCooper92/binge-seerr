package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.WatchProviderGrid
import com.binge.designsystem.component.WatchProviderGridSkeleton
import com.binge.designsystem.component.WatchProviderUi
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.feature.discover.R

/**
 * The streaming services of the region, in the order the title page lists them, as the logo tiles Binge's services step
 * uses, to tick on and off. A row on top opens the region's choice sheet, and a search narrows the tiles.
 */
@Composable
fun WherePage(
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
    var query by rememberSaveable(region) { mutableStateOf("") }
    Column(verticalArrangement = chipGap) {
        ListRow(onClick = onOpenRegion, verticalAlignment = Alignment.CenterVertically, modifier = inset) { modifier ->
            Column(modifier) {
                Text(stringResource(R.string.grid_filter_region), style = MaterialTheme.typography.labelMedium)
                Text(regionName, style = MaterialTheme.typography.titleMedium)
            }
        }
        val providers = region?.let { vocabulary.providers[it] }
        when {
            region != null && region in vocabulary.providersFailed && providers == null ->
                Text(stringResource(R.string.grid_filter_providers_failed), style = MaterialTheme.typography.bodyMedium, modifier = inset)
            region == null || providers?.isEmpty() == true ->
                Text(stringResource(R.string.grid_filter_providers_none), style = MaterialTheme.typography.bodyMedium, modifier = inset)
            providers == null ->
                WatchProviderGridSkeleton(
                    modifier = inset,
                    description = stringResource(R.string.grid_filter_providers_loading),
                )
            else -> ProviderTiles(providers, query, { query = it }, filters, region, inset, onChange)
        }
    }
}

@Composable
private fun ProviderTiles(
    providers: List<FilterOption>,
    query: String,
    onQueryChange: (String) -> Unit,
    filters: DiscoverFilters,
    region: String,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    Column(verticalArrangement = chipGap) {
        BingeSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onClear = { onQueryChange("") },
            placeholder = stringResource(R.string.grid_filter_providers_search),
            modifier = inset,
        )
        val shown = providers.matching(query)
        if (shown.isEmpty()) {
            Text(stringResource(R.string.grid_filter_search_none), style = MaterialTheme.typography.bodyMedium, modifier = inset)
        }
        WatchProviderGrid(
            providers = shown.map { WatchProviderUi(it.id.toInt(), it.name, it.imageUrl.orEmpty()) },
            selectedWatchProviderIds = filters.providers,
            onCheckedChange = { id, _ -> onChange(filters.copy(providers = filters.providers.toggled(id), region = region)) },
            modifier = inset,
        )
    }
}

/** The options whose name has [query] in it, any case; all of them for a blank query. */
fun List<FilterOption>.matching(query: String): List<FilterOption> =
    if (query.isBlank()) this else filter { it.name.contains(query.trim(), ignoreCase = true) }
