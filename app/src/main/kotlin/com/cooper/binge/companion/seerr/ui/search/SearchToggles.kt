package com.cooper.binge.companion.seerr.ui.search

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.ui.settings.server.NetworkViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.settings.server.ServerUsersViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEventSnackbarEffect
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.SaveAsMadeEditorWithExtrasViewModel
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.uiandroid.search.SearchToggle

/** The pages whose editors a search switch can hold, so the search shows each one's failures once however many rows it has. */
internal enum class SwitchPage { General, Network, Users }

/**
 * The switch a result for [entry] carries, or null where it keeps its chevron. For a server setting it is the page's own editor: the same view model,
 * so a flip is one `edit` on the draft that saves as made, with the same failures. The editor is made when the first of its rows
 * is drawn, which is also when [onUsed] says so, and its settings record is read then and not before. While that read has not
 * answered, or has failed, the row stays a plain navigation row.
 */
@Composable
internal fun searchToggle(
    entry: SettingEntry<SeerrSearchTarget>,
    mayChange: Boolean,
    onUsed: (SwitchPage) -> Unit,
): SearchToggle? {
    val row = entry.row ?: return null
    val target = entry.target.target
    if (target is SearchTarget.ThisApp) return deviceToggle(row)
    val page = (target as? SearchTarget.Server)?.page ?: return null
    return when (page) {
        ServerSettingsPage.General ->
            generalSwitches[row]?.let { switch ->
                val vm = hiltViewModel<ServerGeneralViewModel>()
                ExtrasSwitch(vm, switch, mayChange) { onUsed(SwitchPage.General) }
            }
        ServerSettingsPage.Users ->
            usersSwitches[row]?.let { switch ->
                val vm = hiltViewModel<ServerUsersViewModel>()
                ExtrasSwitch(vm, switch, mayChange) { onUsed(SwitchPage.Users) }
            }
        ServerSettingsPage.Network ->
            networkSwitches[row]?.let { switch ->
                val vm = hiltViewModel<NetworkViewModel>()
                val state by vm.uiState.collectAsStateWithLifecycle()
                Used { onUsed(SwitchPage.Network) }
                (state as? EditorUiState.Ready)?.let { switch.toggle(it.draft, it.saveStatus == SaveStatus.Saving, mayChange, vm::edit) }
            }
        else -> null
    }
}

/** The toggle for a This app setting, from the device's own preferences; null for a row that is not a switch. */
@Composable
private fun deviceToggle(row: String): SearchToggle? {
    val switch = deviceSwitches[row] ?: return null
    val vm = hiltViewModel<AppSwitchesViewModel>()
    val app by vm.settings.collectAsStateWithLifecycle()
    return app?.let { switch.toggle(it) { on -> vm.set(switch, on) } }
}

@Composable
private fun <T, X> ExtrasSwitch(
    vm: SaveAsMadeEditorWithExtrasViewModel<T, X>,
    switch: SearchSwitch<T>,
    mayChange: Boolean,
    used: () -> Unit,
): SearchToggle? {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Used(used)
    return (state as? ExtrasEditorUiState.Ready)?.let { switch.toggle(it.draft, it.saveStatus == SaveStatus.Saving, mayChange, vm::edit) }
}

/** Tells the entry, once, that a page's editor is in use. */
@Composable
private fun Used(report: () -> Unit) {
    LaunchedEffect(Unit) { report() }
}

/**
 * Shows what a switch's write answers, as its page does: the same snackbar, or the same dialog for a refusal the server explained.
 * One per page in use, not one per row.
 */
@Composable
internal fun SwitchPageEvents(
    page: SwitchPage,
    snackbarHostState: SnackbarHostState,
) {
    val events =
        when (page) {
            SwitchPage.General -> hiltViewModel<ServerGeneralViewModel>().events
            SwitchPage.Network -> hiltViewModel<NetworkViewModel>().events
            SwitchPage.Users -> hiltViewModel<ServerUsersViewModel>().events
        }
    EditorEventSnackbarEffect(events, snackbarHostState)
}
