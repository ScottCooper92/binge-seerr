package com.cooper.binge.companion.seerr.ui.grid

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.CheckboxRow
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.feature.discover.R
import java.util.IllformedLocaleException
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
fun statusLabel(code: Int): Int? = TV_STATUSES.firstOrNull { it.first == code }?.second

/**
 * The name of a country in the user's language, from its ISO code; the code itself where the device has no name for it. TMDB also
 * lists a subdivision as a code of its own ("CA-QC"), which is the country's name with the subdivision after it.
 */
fun countryName(code: String): String {
    val region = code.substringBefore(SUBDIVISION_SEPARATOR)
    val name =
        try {
            Locale
                .Builder()
                .setRegion(region)
                .build()
                .displayCountry
        } catch (_: IllformedLocaleException) {
            ""
        }.ifBlank { region }
    val subdivision = code.substringAfter(SUBDIVISION_SEPARATOR, missingDelimiterValue = "")
    return if (subdivision.isEmpty()) name else "$name ($subdivision)"
}

private const val SUBDIVISION_SEPARATOR = '-'

val chipGap @Composable get() = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))

/**
 * A country's age ratings: its ratings in a row first, of which one is chosen, then the countries to pick from. The row is
 * what people change and the country list is long, so the row stays above the fold. Picking a country clears the rating.
 */
@Composable
fun CertificationPage(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    val country = filters.certificationCountry ?: vocabulary.defaultRegion?.takeIf { it in vocabulary.certifications }
    val ratings = vocabulary.certifications[country].orEmpty()
    Column(verticalArrangement = chipGap) {
        if (ratings.isNotEmpty()) {
            Text(stringResource(R.string.grid_filter_cert_rating), style = MaterialTheme.typography.titleSmall, modifier = inset)
            BingeFilterChipRow(
                items = ratings.map { FilterChipItem(it) },
                selectedIndex = ratings.indexOf(filters.certification),
                onSelect = { index ->
                    val rating = ratings[index]
                    onChange(
                        filters.copy(certificationCountry = country, certification = rating.takeUnless { it == filters.certification }),
                    )
                },
            )
        }
        Text(stringResource(R.string.grid_filter_cert_country), style = MaterialTheme.typography.titleSmall, modifier = inset)
        ItemGroup(
            title = null,
            rows =
                vocabulary.certifications.keys.sortedBy(::countryName).map { code ->
                    ListItem(
                        icon = Icons.Filled.Public,
                        label = countryName(code),
                        selected = code == country,
                        trailingContent = { if (code == country) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = { onChange(filters.copy(certificationCountry = code, certification = null)) },
                    )
                },
            modifier = inset,
        )
    }
}

/** A search over the server's keywords, the ones chosen as chips to drop, and the matches as rows to pick from. */
@Composable
fun KeywordsPage(
    chosen: Set<Int>,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    loads: FilterLoads,
    onChange: (Set<Int>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(chosen) { loads.onNameKeywords(chosen) }
    LaunchedEffect(Unit) { loads.onKeywordQuery(query) }
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            outlined = true,
        )
        if (chosen.isNotEmpty()) {
            Column(modifier = inset) {
                chosen.sorted().forEach { id ->
                    CheckboxRow(
                        label = vocabulary.keywordNames[id] ?: id.toString(),
                        checked = true,
                        onCheckedChange = { onChange(chosen - id) },
                        icon = DiscoverFilterSection.Keywords.icon,
                    )
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
                    CheckboxRow(
                        label = keyword.name,
                        checked = selected,
                        onCheckedChange = { onChange(chosen.toggled(id)) },
                        icon = DiscoverFilterSection.Keywords.icon,
                    )
                }
            }
    }
}

/** A show's status, any of which a title may have. */
@Composable
fun StatusPage(
    filters: DiscoverFilters,
    inset: Modifier,
    onChange: (DiscoverFilters) -> Unit,
) {
    Column(modifier = inset) {
        TV_STATUSES.forEach { (code, label) ->
            CheckboxRow(
                label = stringResource(label),
                checked = code in filters.status,
                onCheckedChange = { onChange(filters.copy(status = filters.status.toggled(code))) },
                icon = DiscoverFilterSection.Status.icon,
            )
        }
    }
}
