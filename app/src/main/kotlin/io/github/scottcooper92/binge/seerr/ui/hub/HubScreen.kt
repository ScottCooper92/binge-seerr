package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.DisconnectButton
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import com.binge.designsystem.R as DesR

class HubActions(
    val onOpenSection: (HubSection) -> Unit,
    val onOpenAccount: (userId: Int) -> Unit,
    val onRetry: () -> Unit,
    /** The sign-in form on the saved server, for a session it rejected. */
    val onReconnect: () -> Unit,
    val onDisconnect: () -> Unit,
)

/**
 * The connected hub: the dashboard when the server is healthy, or the problem and its way out when
 * it is not. The fork's name is the title, so the app reads as that server's console.
 *
 * @param selectedSection the section open beside the hub, marked in the Manage group. Null on a
 * window narrow enough that the hub is alone on screen, where nothing is open beside it.
 */
@Composable
fun HubScreen(
    state: HubUiState,
    actions: HubActions,
    selectedSection: HubSection? = null,
) {
    val ready = state as? HubUiState.Ready
    ScreenScaffold(title = ready?.server?.title ?: stringResource(R.string.companion_name)) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.outerPadding())) {
            val inner = padding.innerPadding()
            when {
                ready == null -> LoadingScreen(Modifier.padding(inner))
                ready.health.isProblem() ->
                    ConnectionProblem(ready.health, actions.onRetry, actions.onReconnect, actions.onDisconnect, Modifier.padding(inner))
                else -> Dashboard(ready, actions, selectedSection, contentPadding = inner)
            }
        }
    }
}

internal fun ConnectionHealth.isProblem(): Boolean =
    this == ConnectionHealth.Unreachable || this == ConnectionHealth.CouldNotLoad || this == ConnectionHealth.Unauthorized

@Composable
private fun Dashboard(
    state: HubUiState.Ready,
    actions: HubActions,
    selectedSection: HubSection?,
    contentPadding: PaddingValues,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
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
                        selected = section == selectedSection,
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
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryable = health != ConnectionHealth.Unauthorized
    EmptyScreen(
        modifier = modifier,
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
                } else {
                    BingeFilledButton(
                        label = stringResource(R.string.hub_sign_in_again),
                        onClick = onReconnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DisconnectButton(onDisconnect)
                Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            }
        },
    )
}
