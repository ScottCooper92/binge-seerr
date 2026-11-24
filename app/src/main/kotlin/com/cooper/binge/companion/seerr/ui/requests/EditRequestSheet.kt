package com.cooper.binge.companion.seerr.ui.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
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
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.CheckboxRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.ChoiceField
import com.cooper.binge.companion.seerr.ui.DestinationChoices
import com.cooper.binge.companion.seerr.ui.DestinationIcons
import com.cooper.binge.companion.seerr.ui.discover.request.folderOptionLabel
import com.cooper.binge.companion.seerr.ui.discover.request.profileOptionLabel
import com.cooper.binge.companion.seerr.ui.settings.server.TagRows
import com.cooper.binge.companion.seerr.ui.state.MediaStateChip
import com.binge.designsystem.R as DesR

class EditRequestActions(
    val onToggleSeason: (Int) -> Unit,
    val onSelectAllSeasons: (Boolean) -> Unit,
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
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = actions.onDismiss) {
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
        if (edit.seasons.isNotEmpty()) SeasonChecklist(edit = edit, actions = actions, modifier = Modifier.weight(1f, fill = false))
        if (destination != null) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            ) {
                DestinationFields(destination, enabled = !edit.saving, actions = actions) { activeField = it }
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
 * The seasons as a checklist of their own, so a long run scrolls here without dragging the destination
 * fields with it. A show with more than one season the editor may change gets a bulk toggle.
 */
@Composable
private fun SeasonChecklist(
    edit: EditState,
    actions: EditRequestActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (edit.toggleableSeasons.size > 1) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.request_edit_seasons),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                val all = edit.allSeasonsSelected
                BingeTextButton(
                    label = stringResource(if (all) R.string.request_edit_select_none else R.string.request_edit_select_all),
                    onClick = { actions.onSelectAllSeasons(!all) },
                    enabled = !edit.saving,
                )
            }
        }
        LazyColumn {
            itemsIndexed(edit.seasons, key = { _, season -> season.number }) { index, season ->
                SeasonToggleRow(
                    season = season,
                    enabled = !edit.saving,
                    onToggle = { actions.onToggleSeason(season.number) },
                    showDivider = index != edit.seasons.lastIndex,
                )
            }
        }
    }
}

/**
 * The server, profile, root folder and tags. Called inside its own scroll region in
 * [EditRequestContent], independent of the seasons list, since the tag count is server-defined
 * and unbounded.
 */
@Composable
private fun DestinationFields(
    destination: DestinationChoices,
    enabled: Boolean,
    actions: EditRequestActions,
    onOpen: (DestinationField) -> Unit,
) {
    if (destination.loadingChoices) LinearProgressIndicator(Modifier.fillMaxWidth())
    ChoiceField(
        icon = DestinationIcons.server,
        title = stringResource(R.string.advanced_server),
        choices = destination.servers.map { it.id to it.label },
        selected = destination.serverId,
        enabled = enabled,
    ) { onOpen(DestinationField.Server) }
    ChoiceField(
        icon = DestinationIcons.profile,
        title = stringResource(R.string.advanced_profile),
        choices = destination.profiles.map { it.id to profileOptionLabel(it.label, it.id == destination.defaultProfileId) },
        selected = destination.profileId,
        enabled = enabled,
    ) { onOpen(DestinationField.Profile) }
    ChoiceField(
        icon = DestinationIcons.rootFolder,
        title = stringResource(R.string.advanced_root_folder),
        choices = destination.rootFolders.map { it to folderOptionLabel(it, destination.freeSpace[it]) },
        selected = destination.rootFolder,
        enabled = enabled,
    ) { onOpen(DestinationField.RootFolder) }
    TagRows(destination.tags, destination.tagIds, enabled, actions.onToggleTag)
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
                icon = DestinationIcons.server,
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
                icon = DestinationIcons.profile,
                title = stringResource(R.string.advanced_profile),
                choices = destination.profiles.map { it.id to profileOptionLabel(it.label, it.id == destination.defaultProfileId) },
                selected = destination.profileId,
                onSelect = { id ->
                    actions.onSelectProfile(id)
                    onBack()
                },
                onBack = onBack,
            )
        DestinationField.RootFolder ->
            DestinationPickerPanel(
                icon = DestinationIcons.rootFolder,
                title = stringResource(R.string.advanced_root_folder),
                choices = destination.rootFolders.map { it to folderOptionLabel(it, destination.freeSpace[it]) },
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

@Composable
private fun SeasonToggleRow(
    season: SeasonChoice,
    enabled: Boolean,
    onToggle: () -> Unit,
    showDivider: Boolean,
) {
    CheckboxRow(
        label = season.name ?: stringResource(R.string.request_season_number, season.number),
        supportingText = pluralStringResource(R.plurals.request_episodes, season.episodeCount, season.episodeCount),
        checked = season.selected || season.locked,
        onCheckedChange = { onToggle() },
        enabled = enabled && !season.locked,
        showDivider = showDivider,
        leadingContent = { RowIconBox(Icons.Filled.Tv) },
        trailingContent = season.heldStatus?.let { status -> { MediaStateChip(status = status, download = season.heldDownload) } },
    )
}
