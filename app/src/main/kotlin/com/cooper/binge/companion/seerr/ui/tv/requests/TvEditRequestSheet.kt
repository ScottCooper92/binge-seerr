package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvCheckboxRow
import com.binge.designsystem.tv.component.TvChoiceRow
import com.binge.designsystem.tv.component.TvChoiceUi
import com.binge.designsystem.tv.component.TvOptionRow
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetStepFocus
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.DestinationChoices
import com.cooper.binge.companion.seerr.ui.DestinationIcons
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.RequestEditorControls
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.SeasonChoice
import com.cooper.binge.companion.seerr.ui.state.labelRes
import com.cooper.binge.companion.seerr.ui.tv.TvFormNote
import com.cooper.binge.companion.seerr.ui.tv.TvFormNoteTone
import com.binge.designsystem.R as DesR

private const val KEY_STANDARD = "standard"
private const val KEY_4K = "4k"

/** What the sheet shows: the form, or one destination field's choices in its place. */
internal enum class EditStep { Form, Server, Profile, RootFolder }

/**
 * The request's editor as a side sheet, the TV form of the phone's `EditRequestSheet`: the version, where the user may
 * move it; a show's seasons as a checklist, those the server holds locked; and the destination, each field a row that
 * swaps its choices in. One Save sends it all. [TvSaveStatus] shows it saving, and a note says when the server refused
 * it. The editor closes itself once the edit lands, and the sheet goes with it, leaving the page to say so. Tags stay on
 * the phone.
 */
@Composable
internal fun ColumnScope.TvEditRequestSheet(
    item: RequestItem,
    edit: EditState?,
    controls: RequestEditorControls,
    onClosed: () -> Unit,
    entryFocus: FocusRequester,
    initialStep: EditStep = EditStep.Form,
) {
    // A null edit on the first frame is the editor still opening; only one that has been open and is gone is a landed save.
    var opened by remember { mutableStateOf(false) }
    val isOpen = edit != null
    LaunchedEffect(isOpen) {
        if (isOpen) {
            opened = true
        } else if (opened) {
            onClosed()
        }
    }
    if (edit == null) return
    var step by rememberSaveable { mutableStateOf(initialStep) }
    val destination = edit.destination
    if (step != EditStep.Form && destination != null) {
        BackHandler { step = EditStep.Form }
        TvDestinationPickerStep(step, destination, controls, entryFocus) { step = EditStep.Form }
        return
    }
    TvSideSheetTitle(stringResource(R.string.request_edit_title))
    item.title?.let { TvSideSheetBody(it) }
    TvEditForm(edit, controls, entryFocus, onOpen = { field -> if (!edit.saving) step = field })
    TvSideSheetStepFocus(entryFocus)
}

/** The form: the version, the seasons, the destination rows, how the last save went, and Save. */
@Composable
private fun ColumnScope.TvEditForm(
    edit: EditState,
    controls: RequestEditorControls,
    entryFocus: FocusRequester,
    onOpen: (EditStep) -> Unit,
) {
    val entry = edit.entryTarget()
    val firstOpenSeason = edit.seasons.firstOrNull { !it.locked }?.number
    Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        if (edit.can4k) TvVersionChoice(edit, controls, entryFocus)
        edit.seasons.forEach { season ->
            TvSeasonCheckbox(
                season = season,
                onToggle = { if (!edit.saving) controls.toggleSeason(season.number) },
                modifier =
                    if (entry == EditEntry.Season &&
                        season.number == firstOpenSeason
                    ) {
                        Modifier.focusRequester(entryFocus)
                    } else {
                        Modifier
                    },
            )
        }
        edit.destination?.let { TvDestinationRows(it, entryFocus.takeIf { entry == EditEntry.Destination }, onOpen) }
        // A refused save is a note rather than TvSaveStatus's own line, whose Retry does not fit the sheet's width: Save
        // below is the retry.
        if (edit.failed && !edit.saving) {
            TvFormNote(stringResource(DesR.string.save_status_failed), tone = TvFormNoteTone.Error)
        } else {
            TvSaveStatus(status = edit.saveStatus(), onRetry = controls::save)
        }
        // Kept focusable while it saves, so focus is not dropped from under the press; the editor ignores a second save.
        TvSideSheetRow(
            label = stringResource(R.string.request_edit_save),
            onClick = { if (edit.canSave) controls.save() },
            enabled = edit.canSave || edit.saving,
            modifier = if (entry == EditEntry.Save) Modifier.focusRequester(entryFocus) else Modifier,
        )
    }
}

/** Standard or 4K. OK commits, so moving across the pair changes nothing until the user presses. */
@Composable
private fun TvVersionChoice(
    edit: EditState,
    controls: RequestEditorControls,
    entryFocus: FocusRequester,
) {
    TvChoiceRow(
        choices =
            listOf(
                TvChoiceUi(KEY_STANDARD, stringResource(R.string.media_instance_standard)),
                TvChoiceUi(KEY_4K, stringResource(R.string.settings_service_4k)),
            ),
        selectedKey = if (edit.is4k) KEY_4K else KEY_STANDARD,
        onSelect = { key -> if (!edit.saving) controls.select4k(key == KEY_4K) },
        entryFocusRequester = entryFocus,
    )
}

/** Which of the form's controls the sheet lands on: the first the user can change. */
private enum class EditEntry { Version, Season, Destination, Save }

private fun EditState.entryTarget(): EditEntry =
    when {
        can4k -> EditEntry.Version
        seasons.any { !it.locked } -> EditEntry.Season
        destination != null -> EditEntry.Destination
        else -> EditEntry.Save
    }

private fun EditState.saveStatus(): SaveStatus = if (saving) SaveStatus.Saving else SaveStatus.Idle

/** One season: ticked when the request covers it, and locked with the server's state where the server holds it. */
@Composable
private fun TvSeasonCheckbox(
    season: SeasonChoice,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val held = season.heldStatus
    TvCheckboxRow(
        label = season.name ?: stringResource(R.string.request_season_number, season.number),
        checked = season.selected || season.locked,
        onCheckedChange = { onToggle() },
        enabled = !season.locked,
        icon = Icons.Filled.Tv,
        supportingText =
            if (held != null) {
                stringResource(held.labelRes())
            } else {
                pluralStringResource(R.plurals.request_episodes, season.episodeCount, season.episodeCount)
            },
        modifier = modifier,
    )
}

/** The destination's three fields, each a row naming its current choice that opens the choices in its place. */
@Composable
private fun TvDestinationRows(
    destination: DestinationChoices,
    entryFocus: FocusRequester?,
    onOpen: (EditStep) -> Unit,
) {
    val loading = stringResource(R.string.tv_loading).takeIf { destination.loadingChoices }
    TvSideSheetRow(
        label = stringResource(R.string.advanced_server),
        supportingText = destination.servers.firstOrNull { it.id == destination.serverId }?.label ?: loading,
        icon = DestinationIcons.server,
        onClick = { onOpen(EditStep.Server) },
        modifier = entryFocus?.let { Modifier.focusRequester(it) } ?: Modifier,
    )
    TvSideSheetRow(
        label = stringResource(R.string.advanced_profile),
        supportingText = destination.profiles.firstOrNull { it.id == destination.profileId }?.label ?: loading,
        icon = DestinationIcons.profile,
        onClick = { onOpen(EditStep.Profile) },
    )
    TvSideSheetRow(
        label = stringResource(R.string.advanced_root_folder),
        supportingText = destination.rootFolder ?: loading,
        icon = DestinationIcons.rootFolder,
        onClick = { onOpen(EditStep.RootFolder) },
    )
}

/** One destination field's choices, in the form's place; a pick applies and goes back to the form. */
@Composable
private fun ColumnScope.TvDestinationPickerStep(
    step: EditStep,
    destination: DestinationChoices,
    controls: RequestEditorControls,
    entryFocus: FocusRequester,
    onBack: () -> Unit,
) {
    when (step) {
        EditStep.Server ->
            TvPickerStep(
                title = stringResource(R.string.advanced_server),
                choices = destination.servers.map { it.id to it.label },
                selected = destination.serverId,
                icon = DestinationIcons.server,
                onSelect = controls::selectServer,
                entryFocus = entryFocus,
                onBack = onBack,
            )
        EditStep.Profile ->
            TvPickerStep(
                title = stringResource(R.string.advanced_profile),
                choices = destination.profiles.map { it.id to it.label },
                selected = destination.profileId,
                icon = DestinationIcons.profile,
                onSelect = controls::selectProfile,
                entryFocus = entryFocus,
                onBack = onBack,
            )
        EditStep.RootFolder ->
            TvPickerStep(
                title = stringResource(R.string.advanced_root_folder),
                choices = destination.rootFolders.map { it to it },
                selected = destination.rootFolder,
                icon = DestinationIcons.rootFolder,
                onSelect = controls::selectRootFolder,
                entryFocus = entryFocus,
                onBack = onBack,
            )
        EditStep.Form -> Unit
    }
}

/** A field's choices, the current one ticked. With none loaded yet it says so, and Done goes back to the form. */
@Composable
private fun <T> ColumnScope.TvPickerStep(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector,
    onSelect: (T) -> Unit,
    entryFocus: FocusRequester,
    onBack: () -> Unit,
) {
    TvSideSheetTitle(title)
    if (choices.isEmpty()) {
        TvSideSheetBody(stringResource(R.string.tv_loading))
        TvSideSheetRow(label = stringResource(R.string.editor_done), onClick = onBack, modifier = Modifier.focusRequester(entryFocus))
    } else {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            choices.forEachIndexed { index, (key, label) ->
                TvOptionRow(
                    label = label,
                    selected = key == selected,
                    icon = icon,
                    onClick = {
                        if (key != selected) onSelect(key)
                        onBack()
                    },
                    modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
                )
            }
        }
    }
    TvSideSheetStepFocus(entryFocus)
}
