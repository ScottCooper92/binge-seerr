package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.CheckboxRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.request.SeasonSelection
import com.cooper.binge.companion.seerr.ui.discover.title.label

/** A season's name: the server's, else "Season N" (or "Specials" for season 0), so a show that names none still reads. */
@Composable
internal fun seasonLabel(
    number: Int,
    name: String,
): String =
    name.ifBlank {
        if (number == 0) stringResource(R.string.season_specials) else stringResource(R.string.season_name, number)
    }

/**
 * A show's seasons for the version being requested. With partial requests on, each open season is a checkbox, with "All
 * seasons" above; one the server already holds or has been asked for is shown as such and cannot be picked. With them off, the
 * server takes the whole show, so the sheet says what that is and offers no choice.
 */
@Composable
internal fun SeasonStep(
    selection: SeasonSelection,
    enabled: Boolean,
    actions: RequestSheetActions,
) {
    Column {
        selection.limit?.takeIf { it > 0 }?.let { QuotaNote(selection, it) }
        if (selection.partialAllowed) {
            if (selection.open.size > 1) {
                CheckboxRow(
                    label = stringResource(R.string.request_sheet_all_seasons),
                    checked = selection.allOpenPicked,
                    onCheckedChange = { actions.onAllSeasons() },
                    enabled = enabled && selection.canPickAll,
                    icon = Icons.Filled.DoneAll,
                    showDivider = true,
                )
            }
            selection.seasons.forEachIndexed { index, season ->
                SeasonRow(
                    season,
                    picked = season.number in selection.picked,
                    selection = selection,
                    enabled = enabled,
                    last = index == selection.seasons.lastIndex,
                    actions,
                )
            }
        } else {
            CheckboxRow(
                label = stringResource(R.string.request_sheet_remaining_seasons),
                checked = true,
                onCheckedChange = {},
                enabled = false,
                icon = Icons.Filled.DoneAll,
                trailingContent = { Text(selection.open.size.toString(), style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Composable
private fun SeasonRow(
    season: SeasonChoice,
    picked: Boolean,
    selection: SeasonSelection,
    enabled: Boolean,
    last: Boolean,
    actions: RequestSheetActions,
) {
    val status = season.availability.label()?.first
    val episodes = pluralStringResource(R.plurals.season_episodes, season.episodeCount, season.episodeCount)
    CheckboxRow(
        label = seasonLabel(season.number, season.name),
        checked = picked || !season.isOpen,
        onCheckedChange = { actions.onSeason(season.number) },
        enabled = enabled && season.isOpen && (picked || selection.canPickMore),
        supportingText = status?.let { stringResource(R.string.series_season_subtitle, episodes, it) } ?: episodes,
        supportingMaxLines = 2,
        icon = Icons.Filled.Tv,
        showDivider = !last,
    )
}

/** What the quota allows: how many seasons can still be picked, or that the whole show is more than it has left. */
@Composable
private fun QuotaNote(
    selection: SeasonSelection,
    limit: Int,
) {
    val text =
        when {
            selection.overLimit -> stringResource(R.string.request_sheet_seasons_over, selection.open.size)
            selection.partialAllowed -> pluralStringResource(R.plurals.request_sheet_seasons_limit, limit, limit)
            else -> return
        }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
