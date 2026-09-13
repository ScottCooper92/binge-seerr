package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.notifications.NotificationSignal
import io.github.scottcooper92.binge.seerr.ui.DisconnectButton
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

class SettingsActions(
    val onBack: () -> Unit,
    val onEditConnection: () -> Unit,
    val onOpenServerSettings: () -> Unit,
    val onToggleSignal: (NotificationSignal, Boolean) -> Unit,
    val onNotificationAccessChanged: () -> Unit,
    val onDisconnect: () -> Unit,
)

/**
 * Settings: the connection and a way to edit it, then the admin's view of the server — the general
 * settings open their own page for editing; the other groups read until their phase.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    Scaffold(topBar = { BingeTopBar(title = stringResource(R.string.hub_section_settings), onBack = actions.onBack) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                SettingsUiState.Loading -> LoadingScreen()
                is SettingsUiState.Ready -> SettingsContent(state, actions)
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Ready,
    actions: SettingsActions,
) {
    val config = state.config
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Group(stringResource(R.string.settings_group_connection), connectionRows(state.connection, state.server, actions.onEditConnection))
        config?.general?.let { Group(stringResource(R.string.settings_group_general), generalRows(it, actions.onOpenServerSettings)) }
        config?.services?.takeIf { it.isNotEmpty() }?.let { Group(stringResource(R.string.settings_group_services), serviceRows(it)) }
        config?.requestPolicy?.let { Group(stringResource(R.string.settings_group_requests), requestPolicyRows(it)) }
        state.notifications?.let { Group(stringResource(R.string.settings_group_notify_me), notificationRows(it, actions)) }
        config?.agents?.let { Group(stringResource(R.string.settings_group_notifications), agentRows(it)) }
        config?.system?.let { Group(stringResource(R.string.settings_group_system), systemRows(it)) }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        DisconnectButton(actions.onDisconnect, modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)))
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
    SettingsGroup(title = title, rows = rows, modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)))
}
