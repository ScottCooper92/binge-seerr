package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.WatchProviderGrid
import com.binge.designsystem.component.WatchProviderGridSkeleton
import com.binge.designsystem.component.WatchProviderUi
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.feature.discover.R

/**
 * The streaming services of the region, in the order the title page lists them, as the logo tiles Binge's services step
 * uses, to tick on and off. The region is the one the filters carry, or the server's default; the page does not offer another.
 */
@Composable
fun WherePage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    loads: FilterLoads,
    onChange: (DiscoverFilters) -> Unit,
) {
    val region = filters.region ?: vocabulary.defaultRegion
    LaunchedEffect(region) { region?.let(loads.onProviders) }
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
        else -> ProviderTiles(providers, filters, region, inset, onChange)
    }
}

@Composable
private fun ProviderTiles(
    providers: List<FilterOption>,
    filters: DiscoverFilters,
    region: String,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    Column {
        WatchProviderGrid(
            providers = providers.map { WatchProviderUi(it.id.toInt(), it.name, it.imageUrl.orEmpty()) },
            selectedWatchProviderIds = filters.providers,
            onCheckedChange = { id, _ -> onChange(filters.copy(providers = filters.providers.toggled(id), region = region)) },
            modifier = inset,
        )
    }
}
