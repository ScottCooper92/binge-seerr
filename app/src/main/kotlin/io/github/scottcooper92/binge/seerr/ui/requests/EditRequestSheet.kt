package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeSheetFooter
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import com.binge.designsystem.R as DesR

class EditRequestActions(
    val onToggleSeason: (Int) -> Unit,
    val onSelectServer: (Int) -> Unit,
    val onSelectProfile: (Int) -> Unit,
    val onSelectRootFolder: (String) -> Unit,
    val onToggleTag: (Int) -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The editor: a show's seasons as a checklist, those the server already holds locked, and the
 * destination pickers where the user may choose one. Saving sends the whole new set.
 */
@Composable
internal fun EditRequestSheet(
    item: RequestItem,
    edit: EditState,
    actions: EditRequestActions,
) {
    BingeBottomSheet(onDismissRequest = actions.onDismiss) {
        EditRequestContent(item = item, edit = edit, actions = actions)
    }
}

@Composable
internal fun EditRequestContent(
    item: RequestItem,
    edit: EditState,
    actions: EditRequestActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                ).padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Text(stringResource(R.string.request_edit_title), style = MaterialTheme.typography.titleMedium)
        item.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            edit.seasons.forEach { season -> SeasonToggleRow(season, onToggle = { actions.onToggleSeason(season.number) }) }
            edit.destination?.let { destination ->
                if (destination.loadingChoices) LinearProgressIndicator(Modifier.fillMaxWidth())
                ChoicePicker(
                    title = stringResource(R.string.advanced_server),
                    choices = destination.servers.map { it.id to it.label },
                    selected = destination.serverId,
                    onSelect = actions.onSelectServer,
                )
                ChoicePicker(
                    title = stringResource(R.string.advanced_profile),
                    choices = destination.profiles.map { it.id to it.label },
                    selected = destination.profileId,
                    onSelect = actions.onSelectProfile,
                )
                ChoicePicker(
                    title = stringResource(R.string.advanced_root_folder),
                    choices = destination.rootFolders.map { it to it },
                    selected = destination.rootFolder,
                    onSelect = actions.onSelectRootFolder,
                )
                if (destination.tags.isNotEmpty()) {
                    Text(stringResource(R.string.request_tags), style = MaterialTheme.typography.titleSmall)
                    destination.tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in destination.tagIds,
                            onClick = { actions.onToggleTag(tag.id) },
                            label = { Text(tag.label) },
                        )
                    }
                }
            }
        }
        BingeSheetFooter(
            label = stringResource(R.string.request_edit_save),
            onClick = actions.onSave,
            enabled = edit.canSave,
            loading = edit.saving,
        )
    }
}

@Composable
private fun SeasonToggleRow(
    season: SeasonChoice,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !season.locked, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Checkbox(checked = season.selected || season.locked, onCheckedChange = { onToggle() }, enabled = !season.locked)
        Column(modifier = Modifier.weight(1f)) {
            Text(season.name ?: stringResource(R.string.request_season_number, season.number), style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(R.plurals.request_episodes, season.episodeCount, season.episodeCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        season.heldStatus?.let { MediaStateChip(status = it) }
    }
}
