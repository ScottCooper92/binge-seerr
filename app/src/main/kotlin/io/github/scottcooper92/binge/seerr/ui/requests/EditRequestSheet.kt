package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeBottomSheet
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoiceField
import io.github.scottcooper92.binge.seerr.ui.DestinationChoices
import io.github.scottcooper92.binge.seerr.ui.settings.server.TagChips
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import io.github.scottcooper92.binge.seerr.ui.state.SortContent
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

/** Which destination field's sheet is currently swapped in for the form; see [DestinationPickerPanel]. */
private enum class DestinationField { Server, Profile, RootFolder }

@Composable
internal fun EditRequestContent(
    item: RequestItem,
    edit: EditState,
    actions: EditRequestActions,
    modifier: Modifier = Modifier,
) {
    var activeField by rememberSaveable { mutableStateOf<DestinationField?>(null) }
    val destination = edit.destination
    if (activeField != null && destination != null) {
        BackHandler(onBack = { activeField = null })
        DestinationPickerFor(activeField, destination, actions) { activeField = null }
        return
    }
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
            edit.seasons.forEach { season ->
                SeasonToggleRow(season, enabled = !edit.saving, onToggle = { actions.onToggleSeason(season.number) })
            }
            if (destination != null) {
                if (destination.loadingChoices) LinearProgressIndicator(Modifier.fillMaxWidth())
                ChoiceField(
                    title = stringResource(R.string.advanced_server),
                    choices = destination.servers.map { it.id to it.label },
                    selected = destination.serverId,
                    enabled = !edit.saving,
                ) { activeField = DestinationField.Server }
                ChoiceField(
                    title = stringResource(R.string.advanced_profile),
                    choices = destination.profiles.map { it.id to it.label },
                    selected = destination.profileId,
                    enabled = !edit.saving,
                ) { activeField = DestinationField.Profile }
                ChoiceField(
                    title = stringResource(R.string.advanced_root_folder),
                    choices = destination.rootFolders.map { it to it },
                    selected = destination.rootFolder,
                    enabled = !edit.saving,
                ) { activeField = DestinationField.RootFolder }
                TagChips(destination.tags, destination.tagIds, !edit.saving, actions.onToggleTag)
            }
        }
        BingeActionFooter(
            label = stringResource(R.string.request_edit_save),
            onClick = actions.onSave,
            enabled = edit.canSave,
            loading = edit.saving,
        )
    }
}

/**
 * Which of [destination]'s fields [field] names, as a [DestinationPickerPanel]. Split out of
 * [EditRequestContent] to keep that composable's own length within the file's length gate.
 */
@Composable
private fun DestinationPickerFor(
    field: DestinationField?,
    destination: DestinationChoices,
    actions: EditRequestActions,
    onBack: () -> Unit,
) {
    when (field) {
        DestinationField.Server ->
            DestinationPickerPanel(
                title = stringResource(R.string.advanced_server),
                choices = destination.servers.map { it.id to it.label },
                selected = destination.serverId,
                onSelect = { id ->
                    actions.onSelectServer(id)
                    onBack()
                },
                onBack = onBack,
            )
        DestinationField.Profile ->
            DestinationPickerPanel(
                title = stringResource(R.string.advanced_profile),
                choices = destination.profiles.map { it.id to it.label },
                selected = destination.profileId,
                onSelect = { id ->
                    actions.onSelectProfile(id)
                    onBack()
                },
                onBack = onBack,
            )
        DestinationField.RootFolder ->
            DestinationPickerPanel(
                title = stringResource(R.string.advanced_root_folder),
                choices = destination.rootFolders.map { it to it },
                selected = destination.rootFolder,
                onSelect = { path ->
                    actions.onSelectRootFolder(path)
                    onBack()
                },
                onBack = onBack,
            )
        null -> Unit
    }
}

/**
 * A picker swapped in for [EditRequestContent]'s own body rather than opened as a second bottom
 * sheet: [EditRequestSheet] is already one, and stacking a [ChoiceRow][io.github.scottcooper92.binge.seerr.ui.ChoiceRow]
 * on top of it would be two windows and two scrims for what a pick closes right back out of — see #336.
 */
@Composable
private fun <T> DestinationPickerPanel(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(DesR.string.cd_navigate_back))
        }
        SortContent(
            title = title,
            choices = choices.map { it.first },
            selected = selected,
            label = { id -> choices.first { it.first == id }.second },
            onSelect = onSelect,
        )
    }
}

@Composable
private fun SeasonToggleRow(
    season: SeasonChoice,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val rowEnabled = enabled && !season.locked
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = rowEnabled, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Checkbox(checked = season.selected || season.locked, onCheckedChange = { onToggle() }, enabled = rowEnabled)
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
