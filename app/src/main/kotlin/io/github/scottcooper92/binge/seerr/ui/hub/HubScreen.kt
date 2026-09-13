package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.DisconnectButton
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

class HubActions(
    val onOpenSection: (HubSection) -> Unit,
    val onOpenAccount: (userId: Int) -> Unit,
    val onRetry: () -> Unit,
    val onDisconnect: () -> Unit,
)

/**
 * The connected hub: the dashboard when the server is healthy, or the problem and its way out when
 * it is not. The fork's name is the title, so the app reads as that server's console.
 */
@Composable
fun HubScreen(
    state: HubUiState,
    actions: HubActions,
) {
    val ready = state as? HubUiState.Ready
    Scaffold(topBar = { BingeTopBar(title = ready?.server?.title ?: stringResource(R.string.companion_name)) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                ready == null -> LoadingScreen()
                ready.health.isProblem() -> ConnectionProblem(ready.health, actions.onRetry, actions.onDisconnect)
                else -> Dashboard(ready, actions)
            }
        }
    }
}

private fun ConnectionHealth.isProblem(): Boolean =
    this == ConnectionHealth.Unreachable || this == ConnectionHealth.CouldNotLoad || this == ConnectionHealth.Unauthorized

@Composable
private fun Dashboard(
    state: HubUiState.Ready,
    actions: HubActions,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ServerCard(server = state.server, overview = state.overview)
        state.overview.account?.let { account ->
            AccountCard(account = account, quota = state.overview.quota, onClick = { actions.onOpenAccount(account.id) })
        }
        if (state.downloading.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.hub_downloading_now, state.downloading.size))
            DownloadingStrip(state.downloading)
        }
        SectionHeader(title = stringResource(R.string.hub_manage))
        SettingsGroup(
            title = null,
            rows =
                state.overview.visibleSections().map { section ->
                    SettingsRow(
                        icon = section.icon,
                        iconTint = section.iconTint(),
                        label = stringResource(section.titleRes),
                        detail = stringResource(section.descriptionRes),
                        badgeCount = section.badgeCount(state.overview),
                        badgeTint = section.badgeTint(),
                        onClick = { actions.onOpenSection(section) },
                    )
                },
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
        )
        Column(
            modifier = Modifier.padding(dimensionResource(DesR.dimen.screen_content_inset)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(
                stringResource(R.string.connected_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DisconnectButton(actions.onDisconnect)
        }
    }
}

/** Server gone (retry), the dashboard not loaded yet (retry), or the session rejected (reconnect). */
@Composable
private fun ConnectionProblem(
    health: ConnectionHealth,
    onRetry: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val retryable = health != ConnectionHealth.Unauthorized
    EmptyScreen(
        title =
            stringResource(
                when (health) {
                    ConnectionHealth.Unauthorized -> R.string.hub_unauthorized_headline
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_headline
                    else -> R.string.hub_unreachable_headline
                },
            ),
        message =
            stringResource(
                when (health) {
                    ConnectionHealth.Unauthorized -> R.string.hub_unauthorized_body
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_body
                    else -> R.string.hub_unreachable_body
                },
            ),
        icon =
            when (health) {
                ConnectionHealth.Unauthorized -> Icons.Filled.Lock
                ConnectionHealth.CouldNotLoad -> Icons.Filled.HourglassEmpty
                else -> Icons.Filled.CloudOff
            },
        action = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            ) {
                if (retryable) {
                    BingeFilledButton(label = stringResource(R.string.hub_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
                }
                DisconnectButton(onDisconnect)
                Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            }
        },
    )
}
