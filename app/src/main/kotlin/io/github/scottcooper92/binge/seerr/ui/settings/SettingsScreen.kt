package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.notifications.NotificationSignal
import io.github.scottcooper92.binge.seerr.ui.DisconnectButton
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerAgent
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import com.binge.designsystem.R as DesR

/**
 * Twelve of these rows opened a server-settings page, and each had its own callback naming that
 * page in the nav host instead of at the row. [onOpenPage] carries the page, so a row says which
 * one it opens and a new page needs no new callback.
 */
class SettingsActions(
    val onBack: () -> Unit,
    val onEditConnection: () -> Unit,
    val onOpenPage: (ServerSettingsPage) -> Unit,
    val onOpenInstance: (ServiceType, Int) -> Unit,
    val onOpenAgent: (ServerAgent) -> Unit,
    val onToggleSignal: (NotificationSignal, Boolean) -> Unit,
    val onNotificationAccessChanged: () -> Unit,
    val onDisconnect: () -> Unit,
)

/**
 * Settings: the connection and a way to edit it, then the admin's view of the server — the general
 * settings open their own page for editing; the other groups read until their phase.
 *
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    showBack: Boolean = true,
) {
    ScreenScaffold(title = stringResource(R.string.hub_section_settings), onBack = actions.onBack.takeIf { showBack }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.outerPadding())) {
            val inner = padding.innerPadding()
            when (state) {
                SettingsUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is SettingsUiState.Ready -> SettingsContent(state, actions, contentPadding = inner)
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Ready,
    actions: SettingsActions,
    contentPadding: PaddingValues,
) {
    val config = state.config
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        ServerAddressPlate(
            baseUrl = state.connection.baseUrl,
            modifier = Modifier.padding(horizontal = resolvedContentInset()),
        )
        Group(
            stringResource(R.string.settings_group_connection),
            connectionRows(state.connection, state.server, actions.onEditConnection, actions.onOpenPage),
        )
        config?.general?.let {
            Group(
                stringResource(R.string.settings_group_general),
                generalRows(it, actions.onOpenPage),
            )
        }
        if (config != null) {
            Group(stringResource(R.string.server_settings_media_server), mediaServerRows(state.server, actions.onOpenPage))
        }
        config?.services?.let {
            Group(stringResource(R.string.settings_group_services), serviceRows(it, actions.onOpenPage, actions.onOpenInstance))
        }
        config?.requestPolicy?.let { Group(stringResource(R.string.settings_group_requests), requestPolicyRows(it)) }
        state.notifications?.let { Group(stringResource(R.string.settings_group_notify_me), notificationRows(it, actions)) }
        config?.agents?.let {
            Group(stringResource(R.string.settings_group_notifications), agentRows(it, actions.onOpenPage, actions.onOpenAgent))
        }
        config?.system?.let {
            Group(
                stringResource(R.string.settings_group_system),
                systemRows(it, actions.onOpenPage),
            )
        }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        DisconnectButton(actions.onDisconnect, modifier = Modifier.padding(horizontal = resolvedContentInset()))
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    }
}

/** A titled group with the screen's spacing above it; skipped when it has no rows. */
@Composable
private fun Group(
    title: String,
    rows: List<SettingsRow>,
) {
    if (rows.isEmpty()) return
    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    SettingsGroup(title = title, rows = rows, modifier = Modifier.padding(horizontal = resolvedContentInset()))
}
