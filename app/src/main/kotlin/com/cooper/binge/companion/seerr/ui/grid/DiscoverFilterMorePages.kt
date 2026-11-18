package com.cooper.binge.companion.seerr.ui.grid

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.BingeSheetFooter
import com.binge.designsystem.component.ListRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import java.util.Locale
import com.binge.designsystem.R as DesR

/** The TMDB status codes a show can be filtered by, in the order the sheet lists them. */
private val TV_STATUSES =
    listOf(
        0 to R.string.grid_filter_status_returning,
        1 to R.string.grid_filter_status_planned,
        2 to R.string.grid_filter_status_production,
        3 to R.string.grid_filter_status_ended,
        4 to R.string.grid_filter_status_cancelled,
        5 to R.string.grid_filter_status_pilot,
    )

@StringRes
internal fun statusLabel(code: Int): Int? = TV_STATUSES.firstOrNull { it.first == code }?.second

/** The name of a country in the user's language, from its ISO code; the code itself where the device has no name for it. */
internal fun countryName(code: String): String =
    Locale
        .Builder()
        .setRegion(code)
        .build()
        .displayCountry
        .ifBlank { code }

private val chipGap @Composable get() = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))

/** The streaming services of the region, as chips to tap on and off, under a row that opens the region's own page. */
@OptIn(ExperimentalLayoutApi::class)
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
    Column(verticalArrangement = chipGap) {
        ListRow(onClick = onOpenRegion, verticalAlignment = Alignment.CenterVertically, modifier = inset) { modifier ->
            Column(modifier) {
                Text(stringResource(R.string.grid_filter_region), style = MaterialTheme.typography.labelMedium)
                Text(regionName, style = MaterialTheme.typography.titleMedium)
            }
        }
        val providers = region?.let { vocabulary.providers[it] }.orEmpty()
        Text(stringResource(R.string.grid_filter_providers), style = MaterialTheme.typography.titleSmall, modifier = inset)
        if (providers.isEmpty()) {
            Text(stringResource(R.string.grid_filter_providers_none), style = MaterialTheme.typography.bodyMedium, modifier = inset)
        } else {
            FlowRow(modifier = inset, horizontalArrangement = chipGap) {
                providers.forEach { provider ->
                    val id = provider.id.toInt()
                    BingeFilterChip(
                        label = provider.name,
                        selected = id in filters.providers,
                        onClick = { onChange(filters.copy(providers = filters.providers.toggled(id), region = region)) },
                    )
                }
            }
        }
    }
}

/** The countries that have streaming providers; choosing another clears the services, as their ids differ by region. */
@Composable
internal fun RegionPage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    onChange: (DiscoverFilters) -> Unit,
) {
    val current = filters.region ?: vocabulary.defaultRegion
    Column(
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = chipGap,
    ) {
        vocabulary.regions.forEach { region ->
            val selected = region.id == current
            ListRow(
                onClick = { if (!selected) onChange(filters.copy(region = region.id, providers = emptySet())) },
                verticalAlignment = Alignment.CenterVertically,
                selected = selected,
                trailingContent = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
            ) { modifier -> Text(region.name, style = MaterialTheme.typography.titleMedium, modifier = modifier) }
        }
    }
}

/** A country's age ratings: the country as chips, then its ratings, of which one is chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CertificationPage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    val country = filters.certificationCountry ?: vocabulary.defaultRegion?.takeIf { it in vocabulary.certifications }
    Column(modifier = inset, verticalArrangement = chipGap) {
        Text(stringResource(R.string.grid_filter_cert_country), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = chipGap) {
            vocabulary.certifications.keys.sortedBy(::countryName).forEach { code ->
                BingeFilterChip(
                    label = countryName(code),
                    selected = code == country,
                    onClick = { onChange(filters.copy(certificationCountry = code, certification = null)) },
                )
            }
        }
        Text(stringResource(R.string.grid_filter_cert_rating), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = chipGap) {
            vocabulary.certifications[country].orEmpty().forEach { rating ->
                BingeFilterChip(
                    label = rating,
                    selected = rating == filters.certification,
                    onClick = {
                        onChange(
                            filters.copy(
                                certificationCountry = country,
                                certification =
                                    rating.takeUnless {
                                        it ==
                                            filters.certification
                                    },
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** A search over the server's keywords, the ones chosen as chips to drop, and the matches as rows to pick from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeywordsPage(
    chosen: Set<Int>,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    loads: FilterLoads,
    onChange: (Set<Int>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(chosen) { loads.onNameKeywords(chosen) }
    Column(verticalArrangement = chipGap) {
        BingeSearchField(
            query = query,
            onQueryChange = {
                query = it
                loads.onKeywordQuery(it)
            },
            onClear = {
                query = ""
                loads.onKeywordQuery("")
            },
            placeholder = stringResource(R.string.grid_filter_keywords_search),
            modifier = inset.fillMaxWidth(),
        )
        if (chosen.isNotEmpty()) {
            FlowRow(modifier = inset, horizontalArrangement = chipGap) {
                chosen.sorted().forEach { id ->
                    val name = vocabulary.keywordNames[id] ?: id.toString()
                    BingeFilterChip(label = name, selected = true, onClick = { onChange(chosen - id) })
                }
            }
        }
        KeywordMatches(query, chosen, vocabulary, inset, onChange)
    }
}

@Composable
private fun KeywordMatches(
    query: String,
    chosen: Set<Int>,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    onChange: (Set<Int>) -> Unit,
) {
    val results = vocabulary.keywordResults
    when {
        query.isBlank() -> Unit
        vocabulary.keywordsFailed -> Text(stringResource(R.string.grid_filter_keywords_failed), modifier = inset)
        results == null -> Unit
        results.isEmpty() -> Text(stringResource(R.string.grid_filter_keywords_none), modifier = inset)
        else ->
            Column(modifier = inset, verticalArrangement = chipGap) {
                results.forEach { keyword ->
                    val id = keyword.id.toInt()
                    val selected = id in chosen
                    ListRow(
                        onClick = { onChange(chosen.toggled(id)) },
                        verticalAlignment = Alignment.CenterVertically,
                        selected = selected,
                        trailingContent = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
                    ) { modifier -> Text(keyword.name, style = MaterialTheme.typography.titleMedium, modifier = modifier) }
                }
            }
    }
}

/** A show's status, any of which a title may have. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatusPage(
    filters: DiscoverFilters,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    FlowRow(modifier = inset, horizontalArrangement = chipGap) {
        TV_STATUSES.forEach { (code, label) ->
            BingeFilterChip(
                label = stringResource(label),
                selected = code in filters.status,
                onClick = { onChange(filters.copy(status = filters.status.toggled(code))) },
            )
        }
    }
}

/** The page the Where to watch page opens for its region, with a Done that goes back. */
@Composable
internal fun ColumnScope.RegionSheetPage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    onChange: (DiscoverFilters) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        Text(
            text = stringResource(R.string.grid_filter_region),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
        RegionPage(filters, vocabulary, onChange)
    }
    BingeSheetFooter { BingeActionFooter(label = stringResource(R.string.grid_filters_done), onClick = onDone) }
}
