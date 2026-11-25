package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.FilterControlHandlers
import com.binge.designsystem.template.FilterControlUi
import com.binge.designsystem.template.FilterOptionUi
import com.binge.designsystem.template.FilterSectionUi
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.feature.discover.R
import com.binge.designsystem.R as DesR

private const val GENRES_ID = "genres"
private const val DISABLED_ALPHA = 0.38f

/**
 * The genres as the design system's filter section: chips that tick when picked, as Binge's are. The server can only include a
 * genre, so the chips take no exclude.
 */
@Composable
fun genresSection(
    filters: DiscoverFilters,
    vocabulary: FilterVocabulary,
): FilterSectionUi =
    FilterSectionUi(
        id = GENRES_ID,
        title = stringResource(DiscoverFilterSection.Genres.title),
        control =
            FilterControlUi.MultiChoice(
                options = vocabulary.genres.map { FilterOptionUi(it.id.toString(), it.name) },
                includedIds = filters.genres.map(Int::toString).toSet(),
                excludedIds = emptySet(),
                includeOnly = true,
            ),
    )

/** A tap on a genre chip adds it to the filters or takes it out. */
fun genresHandlers(
    filters: DiscoverFilters,
    onChange: (DiscoverFilters) -> Unit,
): FilterControlHandlers =
    FilterControlHandlers(onOptionClick = {
        _,
        id,
        ->
        id.toIntOrNull()?.let { onChange(filters.copy(genres = filters.genres.toggled(it))) }
    })

/** Beside the Apply button: whether a title needs any of the picked genres (OR) or all of them (AND), dimmed until two are picked. */
@Composable
fun genresJoinAccessory(
    filters: DiscoverFilters,
    onChange: (DiscoverFilters) -> Unit,
): @Composable RowScope.() -> Unit =
    {
        OrAndToggle(
            any = filters.genresAny,
            onChange = { onChange(filters.copy(genresAny = it)) },
            enabled = filters.genres.size >= 2,
            modifier = Modifier.height(dimensionResource(DesR.dimen.button_filled_height)),
        )
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
                .padding(dimensionResource(DesR.dimen.padding_xxs)),
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
                        .clickable(enabled = enabled) { onChange(value) }
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
