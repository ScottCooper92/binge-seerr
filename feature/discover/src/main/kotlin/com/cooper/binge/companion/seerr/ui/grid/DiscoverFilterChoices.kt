package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheetContent
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.feature.discover.R
import java.util.Locale

/** The filters that are one pick from a long list, so they open a choice sheet rather than a page. */
enum class FilterChoosing {
    Language,
}

/** What the choice list stands for when nothing is set: no real language code is blank. */
const val ANY_LANGUAGE = ""

/** The languages Binge's picker offers first, in its order. */
private val POPULAR_LANGUAGES = listOf("de", "en", "es", "fr", "hi", "it", "ja", "ko", "pt", "zh")

/** A language written in itself ("Français", "日本語"), or null for a code the platform has no name for. */
fun nativeLanguageName(code: String): String? {
    val locale = Locale.forLanguageTag(code)
    return locale
        .getDisplayLanguage(locale)
        .takeIf { it.isNotBlank() && it != code }
        ?.replaceFirstChar { it.titlecase(locale) }
}

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
        FilterChoosing.Language ->
            FilterChoiceSpec(
                title = stringResource(R.string.grid_filter_language),
                choices =
                    BingeChoiceList.Ready(
                        listOf(BingeChoice(ANY_LANGUAGE, stringResource(R.string.grid_filter_any))) +
                            vocabulary.languages.map {
                                BingeChoice(
                                    it.id,
                                    it.name,
                                    supportingText = nativeLanguageName(it.id),
                                    mark = it.id,
                                )
                            },
                    ),
                selected = filters.language ?: ANY_LANGUAGE,
                suggested = POPULAR_LANGUAGES.filter { code -> vocabulary.languages.any { it.id == code } },
                pinned = listOf(ANY_LANGUAGE),
                filterPlaceholder = stringResource(R.string.grid_filter_language_search),
            )
    }

/** The filters after [value] is picked; Any clears the language. */
fun DiscoverFilters.picked(
    choosing: FilterChoosing,
    value: String,
): DiscoverFilters =
    when (choosing) {
        FilterChoosing.Language -> copy(language = value.takeIf { it != ANY_LANGUAGE })
    }

/**
 * A long-list pick as a page of the filter sheet. Without an [onBack] the side sheet draws the Back bar, so the list carries its
 * title as its own header; a bottom sheet draws none, so the page brings its heading with the arrow back. Picking applies and goes back.
 */
@Composable
fun ColumnScope.FilterChoicePage(
    spec: FilterChoiceSpec,
    onSelect: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) SectionPageHeader(title = spec.title, onBack = onBack)
    BingeChoiceSheetContent(
        title = if (onBack == null) spec.title else null,
        choices = spec.choices,
        selected = spec.selected,
        onSelect = onSelect,
        suggested = spec.suggested,
        pinned = spec.pinned,
        dragHandle = false,
        filterPlaceholder = spec.filterPlaceholder,
    )
}
