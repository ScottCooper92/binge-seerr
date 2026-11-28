package com.cooper.binge.companion.seerr.ui.grid

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.annotation.StringRes
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.grid.ReleasePreset
import com.cooper.binge.companion.seerr.feature.discover.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The filters the sheet edits, each a row that opens its own page. */
enum class DiscoverFilterSection(
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

/** What a section is set to, in words, or "Any" for a filter that is not set. */
@Composable
fun DiscoverFilterSection.summary(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
    today: LocalDate,
): String {
    val any = stringResource(R.string.grid_filter_any)
    val number = remember { NumberFormat.getInstance() }
    val locale = LocalConfiguration.current.locales[0]
    return when (this) {
        DiscoverFilterSection.Genres ->
            vocabulary.genres
                .filter { it.id.toInt() in filters.genres }
                .joinToString(", ") { it.name }
                .ifEmpty { any }
        DiscoverFilterSection.Language -> vocabulary.languages.firstOrNull { it.id == filters.language }?.name ?: any
        DiscoverFilterSection.Release -> releaseSummary(filters, today)
        DiscoverFilterSection.Rating ->
            ratingSummary(filters.ratingMin, filters.ratingMax, LocalContext.current.resources, locale) ?: any
        DiscoverFilterSection.Runtime -> runtimeSummary(filters) ?: any
        DiscoverFilterSection.Votes ->
            voteSummary(filters.voteCountMin, filters.voteCountMax, LocalContext.current.resources, locale) ?: any
        DiscoverFilterSection.WhereToWatch ->
            vocabulary.providers[filters.region ?: vocabulary.defaultRegion]
                .orEmpty()
                .filter { it.id.toInt() in filters.providers }
                .joinToString(", ") { it.name }
                .ifEmpty { if (filters.providers.isEmpty()) any else number.format(filters.providers.size) }
        DiscoverFilterSection.Certification -> certificationSummary(filters) ?: any
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

/** What a rating range reads as on its row: minimum only, maximum only or both, or null when neither is set. */
fun ratingSummary(
    min: Number?,
    max: Number?,
    resources: Resources,
    locale: Locale,
): String? {
    val low = min?.let { ratingLabel(it.toFloat(), locale) }
    val high = max?.let { ratingLabel(it.toFloat(), locale) }
    return when {
        low != null && high != null -> resources.getString(R.string.grid_filter_range, low, high)
        low != null -> resources.getString(R.string.grid_filter_rating_value, low)
        high != null -> resources.getString(R.string.grid_filter_rating_max, high)
        else -> null
    }
}

/** What a vote-count range reads as on its row: minimum only, maximum only or both, or null when neither is set. */
fun voteSummary(
    min: Int?,
    max: Int?,
    resources: Resources,
    locale: Locale,
): String? {
    val format = NumberFormat.getInstance(locale)
    return when {
        min != null && max != null -> resources.getString(R.string.grid_filter_votes_between, format.format(min), format.format(max))
        min != null -> resources.getString(R.string.grid_filter_votes_value, format.format(min))
        max != null -> resources.getString(R.string.grid_filter_votes_max, format.format(max))
        else -> null
    }
}

@StringRes
fun ReleasePreset.label(): Int =
    when (this) {
        ReleasePreset.AnyYear -> R.string.grid_filter_release_any
        ReleasePreset.Upcoming -> R.string.grid_filter_release_upcoming
        ReleasePreset.ThisYear -> R.string.grid_filter_release_this_year
        ReleasePreset.LastFiveYears -> R.string.grid_filter_release_last_five
    }

/** The content rating range in words: "Up to 15", "12A and above", "PG only", or "PG – 15"; null while none is set. */
@Composable
private fun certificationSummary(filters: DiscoverFilters): String? {
    val lte = filters.certificationLte
    val gte = filters.certificationGte
    return when {
        lte != null && lte == gte -> stringResource(R.string.grid_filter_cert_only, lte)
        lte != null && gte != null -> stringResource(R.string.grid_filter_cert_range, gte, lte)
        lte != null -> stringResource(R.string.grid_filter_cert_max, lte)
        gte != null -> stringResource(R.string.grid_filter_cert_min, gte)
        else -> null
    }
}
