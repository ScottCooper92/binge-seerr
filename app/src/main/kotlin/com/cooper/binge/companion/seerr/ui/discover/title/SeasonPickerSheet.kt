package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TaskSheetTopClearance
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import com.binge.designsystem.R as DesR

/** The chip in the Episodes header that names the season shown and opens the picker. */
@Composable
internal fun SeasonDropdown(
    season: SeasonInfo,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .widthIn(max = dimensionResource(R.dimen.season_dropdown_max_width))
                .clip(BingeShapes.Chip)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(
                    start = dimensionResource(DesR.dimen.chip_padding_h),
                    end = dimensionResource(DesR.dimen.padding_xs),
                    top = dimensionResource(DesR.dimen.padding_xs),
                    bottom = dimensionResource(DesR.dimen.padding_xs),
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            seasonLabel(season.number, season.name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(dimensionResource(DesR.dimen.padding_xs)))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Every season with its episode count, year and, where the server holds or has been asked for it, its status. The shown one has a check. */
@Composable
internal fun SeasonPickerSheet(
    seasons: List<SeasonInfo>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) { nav ->
        TaskSheetTopClearance(nav)
        SeasonPickerContent(seasons, selected, onSelect)
    }
}

@Composable
internal fun SeasonPickerContent(
    seasons: List<SeasonInfo>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val rows =
        seasons.map { season ->
            val status = season.standard.label()?.first
            val episodes = pluralStringResource(R.plurals.season_episodes, season.episodeCount, season.episodeCount)
            val detail =
                listOfNotNull(
                    season.airYear,
                    status,
                ).fold(episodes) { text, part -> stringResource(R.string.series_season_subtitle, text, part) }
            val isSelected = season.number == selected
            ListItem(
                icon = Icons.Filled.Tv,
                label = seasonLabel(season.number, season.name),
                detail = detail,
                supportingMaxLines = 3,
                selected = isSelected,
                onClick = { onSelect(season.number) },
                trailingContent =
                    if (isSelected) {
                        (
                            {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                    } else {
                        ({ Spacer(Modifier) })
                    },
                if (isSelected) {
                    (
                        {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                } else {
                    null
                },
            )
        }
    ItemGroup(
        title = null,
        rows = rows,
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
    )
}
