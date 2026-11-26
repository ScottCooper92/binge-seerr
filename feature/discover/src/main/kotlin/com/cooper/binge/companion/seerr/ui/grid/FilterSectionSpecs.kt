package com.cooper.binge.companion.seerr.ui.grid

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.binge.designsystem.formatVoteCount
import com.binge.designsystem.template.FilterControlHandlers
import com.binge.designsystem.template.FilterControlUi
import com.binge.designsystem.template.FilterDateBoundsUi
import com.binge.designsystem.template.FilterOptionUi
import com.binge.designsystem.template.FilterSectionUi
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.RATING_SLIDER_MAX
import com.cooper.binge.companion.seerr.discover.grid.RATING_SLIDER_STEPS
import com.cooper.binge.companion.seerr.discover.grid.RUNTIME_SLIDER_MAX_MINUTES
import com.cooper.binge.companion.seerr.discover.grid.RUNTIME_SLIDER_STEPS
import com.cooper.binge.companion.seerr.discover.grid.ReleasePreset
import com.cooper.binge.companion.seerr.discover.grid.VOTE_COUNT_MAX
import com.cooper.binge.companion.seerr.discover.grid.VOTE_SLIDER_LAST_INDEX
import com.cooper.binge.companion.seerr.discover.grid.VOTE_SLIDER_STEPS
import com.cooper.binge.companion.seerr.discover.grid.certificationIncluded
import com.cooper.binge.companion.seerr.discover.grid.cleared
import com.cooper.binge.companion.seerr.discover.grid.longPressCertification
import com.cooper.binge.companion.seerr.discover.grid.ratingSliderPosition
import com.cooper.binge.companion.seerr.discover.grid.releaseYearRange
import com.cooper.binge.companion.seerr.discover.grid.runtimeSliderPosition
import com.cooper.binge.companion.seerr.discover.grid.tapCertification
import com.cooper.binge.companion.seerr.discover.grid.voteAtSliderIndex
import com.cooper.binge.companion.seerr.discover.grid.voteSliderPosition
import com.cooper.binge.companion.seerr.discover.grid.withRatingSliderPosition
import com.cooper.binge.companion.seerr.discover.grid.withRelease
import com.cooper.binge.companion.seerr.discover.grid.withReleaseFromMonth
import com.cooper.binge.companion.seerr.discover.grid.withReleaseToMonth
import com.cooper.binge.companion.seerr.discover.grid.withRuntimeSliderPosition
import com.cooper.binge.companion.seerr.discover.grid.withVoteSliderPosition
import com.cooper.binge.companion.seerr.feature.discover.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import com.binge.designsystem.R as DesR

private const val GENRES_ID = "genres"
private const val RELEASE_ID = "release"
private const val RATING_ID = "rating"
private const val RUNTIME_ID = "runtime"
private const val VOTES_ID = "votes"
private const val CERTIFICATION_ID = "certification"
private const val FALLBACK_CERTIFICATION_COUNTRY = "US"
private const val DISABLED_ALPHA = 0.38f

/**
 * What the design system's filter section template needs to draw one filter: its control and handlers, the hint under the title, what
 * sits beside the button, and whether the filter has a selection (which reads Apply, not Close, and enables Clear).
 */
data class FilterSectionSpec(
    val ui: FilterSectionUi,
    val handlers: FilterControlHandlers,
    val active: Boolean,
    val hint: String? = null,
    val accessory: (@Composable RowScope.() -> Unit)? = null,
)

/** The section as the template draws it, or null for a filter whose control is its own page (where to watch, keywords, status, ratings). */
@Composable
fun DiscoverFilterSection.spec(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    today: LocalDate,
    onChange: (DiscoverFilters) -> Unit,
): FilterSectionSpec? =
    when (this) {
        DiscoverFilterSection.Genres -> genresSpec(filters, vocabulary, onChange)
        DiscoverFilterSection.Release -> releaseSpec(filters, today, onChange)
        DiscoverFilterSection.Rating -> ratingSpec(filters, onChange)
        DiscoverFilterSection.Runtime -> runtimeSpec(filters, onChange)
        DiscoverFilterSection.Votes -> votesSpec(filters, onChange)
        DiscoverFilterSection.Certification -> certificationSpec(filters, vocabulary, onChange)
        else -> null
    }.let { spec -> spec?.copy(active = filters != filters.cleared(group)) }

/** The genres as chips that tick when picked, as Binge's are. The server can only include a genre, so the chips take no exclude. */
@Composable
private fun DiscoverFilterSection.genresSpec(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    onChange: (DiscoverFilters) -> Unit,
) = FilterSectionSpec(
    ui =
        FilterSectionUi(
            id = GENRES_ID,
            title = stringResource(title),
            control =
                FilterControlUi.MultiChoice(
                    options = vocabulary.genres.map { FilterOptionUi(it.id.toString(), it.name) },
                    includedIds = filters.genres.map(Int::toString).toSet(),
                    excludedIds = emptySet(),
                    includeOnly = true,
                ),
        ),
    handlers =
        FilterControlHandlers(
            onOptionClick = { _, id -> id.toIntOrNull()?.let { onChange(filters.copy(genres = filters.genres.toggled(it))) } },
        ),
    active = false,
    accessory = {
        OrAndToggle(
            any = filters.genresAny,
            onChange = { onChange(filters.copy(genresAny = it)) },
            enabled = filters.genres.size >= 2,
            modifier = Modifier.height(dimensionResource(DesR.dimen.button_filled_height)),
        )
    },
)

/** Quick windows as chips and a From and To month under them, as Binge's year section is. */
@Composable
private fun DiscoverFilterSection.releaseSpec(
    filters: DiscoverFilters,
    today: LocalDate,
    onChange: (DiscoverFilters) -> Unit,
): FilterSectionSpec {
    val presets = ReleasePreset.entries
    val selected = ReleasePreset.of(filters.releaseFrom, filters.releaseTo, today)
    val locale = LocalConfiguration.current.locales[0]
    return FilterSectionSpec(
        ui =
            FilterSectionUi(
                id = RELEASE_ID,
                title = stringResource(title),
                control =
                    FilterControlUi.DateRange(
                        presets = presets.map { FilterOptionUi(it.name, stringResource(it.label())) },
                        selectedPresetId = selected?.name,
                        customTitle = stringResource(R.string.grid_filter_custom_range),
                        bounds =
                            FilterDateBoundsUi(
                                from = filters.releaseFrom?.let(LocalDate::parse),
                                to = filters.releaseTo?.let(LocalDate::parse),
                                fromLabel = stringResource(R.string.grid_filter_from),
                                toLabel = stringResource(R.string.grid_filter_to),
                                emptyLabel = stringResource(R.string.grid_filter_any_date),
                                fromClearDescription = stringResource(R.string.grid_filter_clear_from),
                                toClearDescription = stringResource(R.string.grid_filter_clear_to),
                                today = today,
                                yearRange = releaseYearRange(today),
                                valueLabel = { date, isEnd -> releaseBoundLabel(date, isEnd, locale) },
                            ),
                    ),
            ),
        handlers =
            FilterControlHandlers(
                onSingleSelect = { _, id -> presets.firstOrNull { it.name == id }?.let { onChange(filters.withRelease(it, today)) } },
                onDateBoundChange = { _, isEnd, date ->
                    onChange(
                        when {
                            date == null -> if (isEnd) filters.copy(releaseTo = null) else filters.copy(releaseFrom = null)
                            isEnd -> filters.withReleaseToMonth(YearMonth.from(date))
                            else -> filters.withReleaseFromMonth(YearMonth.from(date))
                        },
                    )
                },
            ),
        active = false,
    )
}

@Composable
private fun DiscoverFilterSection.ratingSpec(
    filters: DiscoverFilters,
    onChange: (DiscoverFilters) -> Unit,
): FilterSectionSpec {
    val locale = LocalConfiguration.current.locales[0]
    return FilterSectionSpec(
        ui =
            FilterSectionUi(
                id = RATING_ID,
                title = stringResource(title),
                control =
                    FilterControlUi.Range(
                        values = filters.ratingSliderPosition(),
                        valueRange = 0f..RATING_SLIDER_MAX,
                        steps = RATING_SLIDER_STEPS,
                        valueLabel = { ratingLabel(it, locale) },
                        startThumbDescription = stringResource(R.string.grid_filter_rating_start),
                        endThumbDescription = stringResource(R.string.grid_filter_rating_end),
                    ),
            ),
        handlers = FilterControlHandlers(onRangeChange = { _, values -> onChange(filters.withRatingSliderPosition(values)) }),
        active = false,
    )
}

@Composable
private fun DiscoverFilterSection.runtimeSpec(
    filters: DiscoverFilters,
    onChange: (DiscoverFilters) -> Unit,
): FilterSectionSpec {
    val resources = LocalResources.current
    return FilterSectionSpec(
        ui =
            FilterSectionUi(
                id = RUNTIME_ID,
                title = stringResource(title),
                control =
                    FilterControlUi.Range(
                        values = filters.runtimeSliderPosition(),
                        valueRange = 0f..RUNTIME_SLIDER_MAX_MINUTES.toFloat(),
                        steps = RUNTIME_SLIDER_STEPS,
                        valueLabel = { resources.getString(R.string.grid_filter_minutes, it.roundToInt()) },
                        startThumbDescription = stringResource(R.string.grid_filter_runtime_start),
                        endThumbDescription = stringResource(R.string.grid_filter_runtime_end),
                        openEndLabel = stringResource(R.string.grid_filter_minutes_open, RUNTIME_SLIDER_MAX_MINUTES),
                    ),
            ),
        handlers = FilterControlHandlers(onRangeChange = { _, values -> onChange(filters.withRuntimeSliderPosition(values)) }),
        active = false,
        hint = stringResource(R.string.grid_filter_hint_range),
    )
}

@Composable
private fun DiscoverFilterSection.votesSpec(
    filters: DiscoverFilters,
    onChange: (DiscoverFilters) -> Unit,
): FilterSectionSpec {
    val locale = LocalConfiguration.current.locales[0]
    return FilterSectionSpec(
        ui =
            FilterSectionUi(
                id = VOTES_ID,
                title = stringResource(title),
                control =
                    FilterControlUi.Range(
                        values = filters.voteSliderPosition(),
                        valueRange = 0f..VOTE_SLIDER_LAST_INDEX.toFloat(),
                        steps = VOTE_SLIDER_STEPS,
                        valueLabel = { index -> voteLabel(voteAtSliderIndex(index), locale) },
                        startThumbDescription = stringResource(R.string.grid_filter_votes_start),
                        endThumbDescription = stringResource(R.string.grid_filter_votes_end),
                        openEndLabel = stringResource(R.string.grid_filter_votes_open, voteLabel(VOTE_COUNT_MAX, locale)),
                    ),
            ),
        handlers = FilterControlHandlers(onRangeChange = { _, values -> onChange(filters.withVoteSliderPosition(values)) }),
        active = false,
        hint = stringResource(R.string.grid_filter_hint_range),
    )
}

/**
 * The ratings of the user's region as chips, as Binge's content rating is: a tap sets the highest a title may be rated and a long press the
 * lowest, and the ratings between them are in while the rest are out. There is no country to pick: the region is the filters' own, then the
 * server's, then the first the server lists.
 */
@Composable
private fun DiscoverFilterSection.certificationSpec(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    onChange: (DiscoverFilters) -> Unit,
): FilterSectionSpec {
    val country = certificationCountry(filters, vocabulary)
    val ratings = vocabulary.certifications[country].orEmpty()
    val lte = filters.certificationLte
    val gte = filters.certificationGte
    val states = ratings.mapIndexed { index, rating -> rating to certificationIncluded(ratings, index, lte, gte) }

    fun edited(range: Pair<String?, String?>) =
        filters.copy(certificationCountry = country, certificationLte = range.first, certificationGte = range.second)
    return FilterSectionSpec(
        ui =
            FilterSectionUi(
                id = CERTIFICATION_ID,
                title = stringResource(title),
                control =
                    FilterControlUi.MultiChoice(
                        options = ratings.map { FilterOptionUi(it, it) },
                        includedIds = states.filter { it.second == true }.map { it.first }.toSet(),
                        excludedIds = states.filter { it.second == false }.map { it.first }.toSet(),
                    ),
            ),
        handlers =
            FilterControlHandlers(
                onOptionClick = { _, rating -> onChange(edited(tapCertification(ratings, lte, gte, rating))) },
                onOptionLongClick = { _, rating -> onChange(edited(longPressCertification(ratings, lte, gte, rating))) },
                optionLongClickLabel = stringResource(R.string.grid_filter_cert_from_label),
            ),
        active = false,
        hint = stringResource(R.string.grid_filter_hint_cert),
    )
}

/** The country whose ratings the filter shows: the one already chosen, the server's region, the United States, then the first listed with ratings. */
fun certificationCountry(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
): String? =
    listOfNotNull(filters.certificationCountry, vocabulary.defaultRegion, FALLBACK_CERTIFICATION_COUNTRY)
        .firstOrNull { vocabulary.certifications[it].orEmpty().isNotEmpty() }
        ?: vocabulary.certifications.keys
            .sorted()
            .firstOrNull { vocabulary.certifications[it].orEmpty().isNotEmpty() }

private const val VOTES_PER_THOUSAND = 1_000

/** A vote count as a stop reads: "10k" for whole thousands in English, anything else through the design system's formatter. */
private fun voteLabel(
    votes: Int,
    locale: Locale,
): String =
    if (locale.language == Locale.ENGLISH.language && votes >= VOTES_PER_THOUSAND && votes % VOTES_PER_THOUSAND == 0) {
        "${votes / VOTES_PER_THOUSAND}k"
    } else {
        votes.formatVoteCount(locale)
    }

/** A rating as a thumb reads: "7" for a whole number and "7.5" for a half, in the device's number form. */
fun ratingLabel(
    rating: Float,
    locale: Locale,
): String = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(rating)

/** What the section reads as on a date field: "Mar 2018" on the month boundary the picker makes, otherwise the full date. */
private fun releaseBoundLabel(
    date: LocalDate,
    isEnd: Boolean,
    locale: Locale,
): String {
    val month = YearMonth.from(date)
    val aligned = if (isEnd) date == month.atEndOfMonth() else date.dayOfMonth == 1
    val pattern = if (aligned) "LLLy" else "yMMMd"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, pattern), locale).format(if (aligned) month else date)
}

@Composable
private fun OrAndToggle(
    any: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = BingeShapes.Pill
    Row(
        modifier =
            modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(dimensionResource(DesR.dimen.hairline_thickness), MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(dimensionResource(DesR.dimen.padding_xxs))
                .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(true to R.string.grid_filter_join_or, false to R.string.grid_filter_join_and).forEach { (value, label) ->
            val selected = value == any
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .clip(shape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onChange(value) })
                        .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
