package com.cooper.binge.companion.seerr.ui.grid

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.CheckboxRow
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.feature.discover.R
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

val chipGap @Composable get() = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))

/** A search over the server's keywords, the ones chosen as chips to drop, and the matches as rows to pick from. */
@Composable
fun KeywordsPage(
    chosen: Set<Int>,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    loads: FilterLoads,
    exclude: Boolean,
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
            HorizontalDivider(modifier = inset, color = MaterialTheme.colorScheme.outlineVariant)
        }
        KeywordMatches(query, chosen, vocabulary, inset, exclude, onChange)
    }
}

@Composable
private fun KeywordMatches(
    query: String,
    chosen: Set<Int>,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    exclude: Boolean,
    onChange: (Set<Int>) -> Unit,
) {
    val results = vocabulary.keywordResults
    // One height for every state, so the sheet does not jump as a search starts, ends or finds nothing.
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = dimensionResource(R.dimen.keyword_matches_min_height)),
        contentAlignment = Alignment.Center,
    ) {
        KeywordMatchesContent(query, chosen, vocabulary, inset, exclude, results, onChange)
    }
}

@Composable
private fun KeywordMatchesContent(
    query: String,
    chosen: Set<Int>,
    vocabulary: FilterVocabulary,
    inset: Modifier,
    exclude: Boolean,
    results: List<FilterOption>?,
    onChange: (Set<Int>) -> Unit,
) {
    when {
        query.isBlank() ->
            if (chosen.isEmpty()) {
                Text(
                    stringResource(if (exclude) R.string.grid_filter_keywords_exclude_hint else R.string.grid_filter_keywords_hint),
                    modifier = inset,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        vocabulary.keywordsFailed -> Text(stringResource(R.string.grid_filter_keywords_failed), modifier = inset)
        results == null -> CircularProgressIndicator(modifier = inset)
        results.isEmpty() ->
            Text(
                stringResource(if (exclude) R.string.grid_filter_keywords_exclude_none else R.string.grid_filter_keywords_none),
                modifier = inset,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
