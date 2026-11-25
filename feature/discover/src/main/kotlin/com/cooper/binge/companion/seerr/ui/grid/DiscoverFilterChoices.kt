package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.feature.discover.R

/** The two filters that are one pick from a long list, so they open a choice sheet rather than a page. */
enum class FilterChoosing { Region, Language }

/** What the choice list stands for when nothing is set: no real language code is blank. */
const val ANY_LANGUAGE = ""

private const val REGIONAL_INDICATOR_A = 0x1F1E6

/** The flag for an ISO 3166 country code, or null for anything that is not two letters. */
fun flagEmoji(code: String): String? =
    code
        .uppercase()
        .takeIf { it.length == 2 && it.all { letter -> letter in 'A'..'Z' } }
        ?.map { String(Character.toChars(REGIONAL_INDICATOR_A + (it - 'A'))) }
        ?.joinToString("")

/** What a choice sheet is given, so the sheet and a frame of its content draw the same list. */
class FilterChoiceSpec(
    val title: String,
    val choices: BingeChoiceList<String>,
    val selected: String?,
    val suggested: List<String>,
    val pinned: List<String>,
    val filterPlaceholder: String,
)

@Composable
fun filterChoiceSpec(
    choosing: FilterChoosing,
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
): FilterChoiceSpec =
    when (choosing) {
        FilterChoosing.Region ->
            FilterChoiceSpec(
                title = stringResource(R.string.grid_filter_region),
                choices = BingeChoiceList.Ready(vocabulary.regions.map { BingeChoice(it.id, it.name, mark = flagEmoji(it.id)) }),
                selected = filters.region ?: vocabulary.defaultRegion,
                suggested = listOfNotNull(vocabulary.defaultRegion).filter { code -> vocabulary.regions.any { it.id == code } },
                pinned = emptyList(),
                filterPlaceholder = stringResource(R.string.grid_filter_region_search),
            )
        FilterChoosing.Language ->
            FilterChoiceSpec(
                title = stringResource(R.string.grid_filter_language),
                choices =
                    BingeChoiceList.Ready(
                        listOf(BingeChoice(ANY_LANGUAGE, stringResource(R.string.grid_filter_any))) +
                            vocabulary.languages.map { BingeChoice(it.id, it.name) },
                    ),
                selected = filters.language ?: ANY_LANGUAGE,
                suggested = emptyList(),
                pinned = listOf(ANY_LANGUAGE),
                filterPlaceholder = stringResource(R.string.grid_filter_language_search),
            )
    }

/** The filters after [value] is picked: another region clears the services, whose ids differ by region; Any clears the language. */
fun DiscoverFilters.picked(
    choosing: FilterChoosing,
    value: String,
): DiscoverFilters =
    when (choosing) {
        FilterChoosing.Region -> if (value == region) this else copy(region = value, providers = emptySet())
        FilterChoosing.Language -> copy(language = value.takeIf { it != ANY_LANGUAGE })
    }

/** The choice sheet for [choosing], over the filter sheet; picking applies and closes it. */
@Composable
fun FilterChoiceSheet(
    choosing: FilterChoosing,
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    onChange: (DiscoverFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val spec = filterChoiceSpec(choosing, filters, vocabulary)
    BingeChoiceSheet(
        title = spec.title,
        choices = spec.choices,
        selected = spec.selected,
        onSelect = { onChange(filters.picked(choosing, it)) },
        onDismissRequest = onDismiss,
        suggested = spec.suggested,
        pinned = spec.pinned,
        filterPlaceholder = spec.filterPlaceholder,
    )
}
