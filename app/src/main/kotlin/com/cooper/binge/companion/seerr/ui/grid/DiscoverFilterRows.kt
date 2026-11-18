package com.cooper.binge.companion.seerr.ui.grid

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ListRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
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
    Release(R.string.grid_filter_release, Icons.Filled.CalendarMonth),
    Rating(R.string.grid_filter_rating, Icons.Filled.Star),
    Runtime(R.string.grid_filter_runtime, Icons.Filled.Schedule),
    Votes(R.string.grid_filter_votes, Icons.Filled.HowToVote),
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

/** One row per filter: its icon and name, and what it is set to on the second line. */
@Composable
internal fun DiscoverFilterRows(
    filters: DiscoverFilters,
    today: LocalDate,
    onOpen: (DiscoverFilterSection) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        DiscoverFilterSection.entries.forEach { section ->
            ListRow(
                onClick = { onOpen(section) },
                verticalAlignment = Alignment.CenterVertically,
                leading = { Icon(section.icon, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            ) { modifier ->
                Column(modifier) {
                    Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = section.summary(filters, today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** What a section is set to, in words, or "Any" for a filter that is not set. */
@Composable
internal fun DiscoverFilterSection.summary(
    filters: DiscoverFilters,
    today: LocalDate,
): String {
    val any = stringResource(R.string.grid_filter_any)
    return when (this) {
        DiscoverFilterSection.Release -> releaseSummary(filters, today)
        DiscoverFilterSection.Rating ->
            filters.ratingMin?.let { stringResource(R.string.grid_filter_rating_value, it.toInt()) } ?: any
        DiscoverFilterSection.Runtime -> runtimeSummary(filters) ?: any
        DiscoverFilterSection.Votes ->
            filters.voteCountMin?.let { stringResource(R.string.grid_filter_votes_value, NumberFormat.getInstance().format(it)) } ?: any
    }
}

@Composable
private fun releaseSummary(
    filters: DiscoverFilters,
    today: LocalDate,
): String {
    val preset = ReleasePreset.of(filters.releaseFrom, filters.releaseTo, today)
    if (preset != null) return stringResource(preset.label())
    val locale = LocalConfiguration.current.locales[0]
    val month = DateTimeFormatter.ofPattern("LLL yyyy", locale)
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
