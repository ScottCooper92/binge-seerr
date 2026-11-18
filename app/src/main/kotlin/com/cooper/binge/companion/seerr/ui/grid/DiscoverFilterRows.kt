package com.cooper.binge.companion.seerr.ui.grid

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.grid.ReleasePreset
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.binge.designsystem.R as DesR

/** The filters the sheet edits, each a row that opens its own page. */
internal enum class DiscoverFilterSection(
    @StringRes val title: Int,
    val icon: ImageVector,
) {
    Genres(R.string.grid_filter_genres, Icons.Filled.Category),
    Release(R.string.grid_filter_release, Icons.Filled.CalendarMonth),
    Rating(R.string.grid_filter_rating, Icons.Filled.Star),
    Runtime(R.string.grid_filter_runtime, Icons.Filled.Schedule),
    Votes(R.string.grid_filter_votes, Icons.Filled.HowToVote),
    Language(R.string.grid_filter_language, Icons.Filled.Language),
    WhereToWatch(R.string.grid_filter_where, Icons.Filled.LiveTv),
    Certification(R.string.grid_filter_cert, Icons.Filled.Shield),
    Keywords(R.string.grid_filter_keywords, Icons.Filled.Tag),
    ExcludeKeywords(R.string.grid_filter_keywords_exclude, Icons.Filled.Block),
    Status(R.string.grid_filter_status, Icons.Filled.Sync),
    ;

    /** The filters the row sets, which the grid shows as one chip. */
    val group: FilterGroup
        get() =
            when (this) {
                Genres -> FilterGroup.Genres
                Release -> FilterGroup.Release
                Rating -> FilterGroup.Rating
                Runtime -> FilterGroup.Runtime
                Votes -> FilterGroup.Votes
                Language -> FilterGroup.Language
                WhereToWatch -> FilterGroup.Where
                Certification -> FilterGroup.Certification
                Keywords -> FilterGroup.Keywords
                ExcludeKeywords -> FilterGroup.ExcludedKeywords
                Status -> FilterGroup.Status
            }

    /** Whether the sheet has anything to offer for this row: a picker with no choices is left out. */
    fun isOffered(
        vocabulary: FilterVocabulary,
        kind: TitleKind,
    ): Boolean =
        when (this) {
            Genres -> vocabulary.genres.isNotEmpty()
            Language -> vocabulary.languages.isNotEmpty()
            WhereToWatch -> vocabulary.capabilities.watch && vocabulary.regions.isNotEmpty()
            Certification -> vocabulary.capabilities.certification && vocabulary.certifications.isNotEmpty()
            Keywords -> vocabulary.capabilities.keywords
            ExcludeKeywords -> vocabulary.capabilities.excludeKeywords
            Status -> vocabulary.capabilities.status && kind == TitleKind.Series
            else -> true
        }
}

/** The header of the sheet: its title, and Clear all once a filter is set. */
@Composable
internal fun FilterSheetHeader(
    showClear: Boolean,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(R.string.grid_filters_title), style = MaterialTheme.typography.titleLarge)
        if (showClear) BingeTextButton(label = stringResource(R.string.grid_filters_clear_all), onClick = onClearAll)
    }
}

/** One row per filter on one grouped card, as Binge's: a tinted icon, its name, and what it is set to on the second line. */
@Composable
internal fun DiscoverFilterRows(
    filters: DiscoverFilters,
    kind: TitleKind,
    vocabulary: FilterVocabulary,
    today: LocalDate,
    onOpen: (DiscoverFilterSection) -> Unit,
) {
    val rows =
        DiscoverFilterSection.entries.filter { it.isOffered(vocabulary, kind) }.map { section ->
            ListItem(
                icon = section.icon,
                label = stringResource(section.title),
                detail = section.summary(filters, vocabulary, today),
                onClick = { onOpen(section) },
            )
        }
    ItemGroup(
        title = null,
        rows = rows,
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
    )
}

/** What a section is set to, in words, or "Any" for a filter that is not set. */
@Composable
internal fun DiscoverFilterSection.summary(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    today: LocalDate,
): String {
    val any = stringResource(R.string.grid_filter_any)
    val number = remember { NumberFormat.getInstance() }
    return when (this) {
        DiscoverFilterSection.Genres ->
            vocabulary.genres
                .filter { it.id.toInt() in filters.genres }
                .joinToString(", ") { it.name }
                .ifEmpty { any }
        DiscoverFilterSection.Language -> vocabulary.languages.firstOrNull { it.id == filters.language }?.name ?: any
        DiscoverFilterSection.Release -> releaseSummary(filters, today)
        DiscoverFilterSection.Rating ->
            filters.ratingMin?.let { stringResource(R.string.grid_filter_rating_value, it.toInt()) } ?: any
        DiscoverFilterSection.Runtime -> runtimeSummary(filters) ?: any
        DiscoverFilterSection.Votes ->
            filters.voteCountMin?.let { stringResource(R.string.grid_filter_votes_value, number.format(it)) } ?: any
        DiscoverFilterSection.WhereToWatch ->
            vocabulary.providers[filters.region ?: vocabulary.defaultRegion]
                .orEmpty()
                .filter { it.id.toInt() in filters.providers }
                .joinToString(", ") { it.name }
                .ifEmpty { if (filters.providers.isEmpty()) any else number.format(filters.providers.size) }
        DiscoverFilterSection.Certification ->
            filters.certification?.let { stringResource(R.string.grid_filter_pair, filters.certificationCountry.orEmpty(), it) } ?: any
        DiscoverFilterSection.Keywords -> keywordSummary(filters.keywords, vocabulary) ?: any
        DiscoverFilterSection.ExcludeKeywords -> keywordSummary(filters.excludeKeywords, vocabulary) ?: any
        DiscoverFilterSection.Status -> {
            val resources = LocalContext.current.resources
            filters.status
                .sorted()
                .mapNotNull(::statusLabel)
                .joinToString(", ") { resources.getString(it) }
                .ifEmpty { any }
        }
    }
}

private fun keywordSummary(
    ids: Set<Int>,
    vocabulary: FilterVocabulary,
): String? = ids.sorted().joinToString(", ") { vocabulary.keywordNames[it] ?: it.toString() }.ifEmpty { null }

@Composable
private fun releaseSummary(
    filters: DiscoverFilters,
    today: LocalDate,
): String {
    val preset = ReleasePreset.of(filters.releaseFrom, filters.releaseTo, today)
    if (preset == ReleasePreset.AnyYear) return stringResource(R.string.grid_filter_any)
    if (preset != null) return stringResource(preset.label())
    val locale = LocalConfiguration.current.locales[0]
    val month = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMyyyy"), locale)
    val from = filters.releaseFrom?.let { LocalDate.parse(it).format(month) }
    val to = filters.releaseTo?.let { LocalDate.parse(it).format(month) }
    return when {
        from != null && to != null -> stringResource(R.string.grid_filter_range, from, to)
        from != null -> stringResource(R.string.grid_filter_release_from, from)
        else -> stringResource(R.string.grid_filter_release_until, to.orEmpty())
    }
}

@Composable
private fun runtimeSummary(filters: DiscoverFilters): String? {
    val min = filters.runtimeMin
    val max = filters.runtimeMax
    return when {
        min != null && max != null -> stringResource(R.string.grid_filter_runtime_between, min, max)
        min != null -> stringResource(R.string.grid_filter_runtime_min, min)
        max != null -> stringResource(R.string.grid_filter_runtime_max, max)
        else -> null
    }
}

@StringRes
internal fun ReleasePreset.label(): Int =
    when (this) {
        ReleasePreset.AnyYear -> R.string.grid_filter_release_any
        ReleasePreset.Upcoming -> R.string.grid_filter_release_upcoming
        ReleasePreset.ThisYear -> R.string.grid_filter_release_this_year
        ReleasePreset.LastFiveYears -> R.string.grid_filter_release_last_five
    }
