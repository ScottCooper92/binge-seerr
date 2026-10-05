package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.HintCard
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.DisconnectButton
import io.github.scottcooper92.binge.seerr.ui.rememberAllowLocalNetwork
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import com.binge.designsystem.R as DesR

class HubActions(
    val onOpenSection: (HubSection) -> Unit,
    val onOpenAccount: (userId: Int) -> Unit,
    val onOpenRequest: (requestId: Int) -> Unit,
    val onRetry: () -> Unit,
    /** The sign-in form on the saved server: for a session the server rejected, and to correct an address that no longer answers. */
    val onReconnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onDismissBingeHint: () -> Unit,
    /** Debug builds only: the rows of the hub's last section. Empty hides the section. */
    val developerRows: List<DeveloperRow> = emptyList(),
)

/**
 * The connected hub: the dashboard when the server is healthy, or the problem and its way out when
 * it is not. The fork's name is the title, so the app reads as that server's console.
 *
 * @param selectedSection the section open beside the hub, marked in the Manage group. Null on a
 * window narrow enough that the hub is alone on screen, where nothing is open beside it.
 * @param admitsUnverifiedCallers whether this build admits Binge's package names under any certificate.
 * True only on a debug build, where it puts a banner at the top of the dashboard so a tester knows what
 * they are running (#679).
 */
@Composable
fun HubScreen(
    state: HubUiState,
    actions: HubActions,
    selectedSection: HubSection? = null,
    admitsUnverifiedCallers: Boolean = false,
) {
    val ready = state as? HubUiState.Ready
    ScreenScaffold(title = ready?.server?.title ?: stringResource(R.string.companion_name)) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.outerPadding())) {
            val inner = padding.innerPadding()
            when {
                state is HubUiState.Error ->
                    ConnectionProblem(state.health, actions.onRetry, actions.onReconnect, actions.onDisconnect, Modifier.padding(inner))
                ready == null -> LoadingScreen(Modifier.padding(inner))
                ready.health.isProblem() ->
                    ConnectionProblem(ready.health, actions.onRetry, actions.onReconnect, actions.onDisconnect, Modifier.padding(inner))
                else -> Dashboard(ready, actions, selectedSection, admitsUnverifiedCallers, contentPadding = inner)
            }
        }
    }
}

internal fun ConnectionHealth.isProblem(): Boolean =
    this == ConnectionHealth.Unreachable ||
        this == ConnectionHealth.LocalNetworkDenied ||
        this == ConnectionHealth.CouldNotLoad ||
        this == ConnectionHealth.Unauthorized

@Composable
private fun Dashboard(
    state: HubUiState.Ready,
    actions: HubActions,
    selectedSection: HubSection?,
    admitsUnverifiedCallers: Boolean,
    contentPadding: PaddingValues,
) {
    val inset = resolvedContentInset()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        if (admitsUnverifiedCallers) {
            HintCard(
                text = stringResource(R.string.hub_unverified_callers),
                icon = Icons.Filled.Warning,
                modifier = Modifier.padding(start = inset, end = inset, bottom = dimensionResource(DesR.dimen.padding_m)),
            )
        }
        ServerCard(server = state.server, overview = state.overview, inset = inset)
        state.overview.account?.let { account ->
            AccountCard(
                account = account,
                quota = state.overview.quota,
                inset = inset,
                onClick = { actions.onOpenAccount(account.id) },
            )
        }
        if (state.downloading.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.hub_downloading_now, state.downloading.size))
            DownloadingStrip(state.downloading, onClick = { actions.onOpenRequest(it.requestId) }, inset = inset)
        }
        SectionHeader(title = stringResource(R.string.hub_manage))
        ItemGroup(
            title = null,
            rows =
                state.overview.visibleSections().map { section ->
                    ListItem(
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
            modifier = Modifier.padding(horizontal = inset),
        )
        Column(
            modifier = Modifier.padding(inset),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            BingeTile(state.bingeStatus, state.bingeHintDismissed, actions.onDismissBingeHint)
            DisconnectButton(actions.onDisconnect)
        }
        if (actions.developerRows.isNotEmpty()) DeveloperGroup(actions.developerRows, inset)
    }
}

/**
 * Binge's relationship to this device, replacing the old static hint (#469): a hint for each of
 * the three states, each with the button that takes the user to the next step and a close control.
 */
@Composable
internal fun BingeTile(
    status: BingeStatus,
    hintDismissed: Boolean,
    onDismissHint: () -> Unit,
) {
    if (hintDismissed) return
    val context = LocalContext.current
    when (status) {
        BingeStatus.NotInstalled ->
            HintCard(
                text = "${stringResource(
                    R.string.hub_binge_not_installed_title,
                )} ${stringResource(R.string.hub_binge_not_installed_detail)}",
                actionLabel = stringResource(R.string.hub_binge_get),
                actionIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onAction = { context.openBingeOnPlayStore() },
                onDismiss = onDismissHint,
            )
        BingeStatus.NotConnected ->
            HintCard(
                text = stringResource(R.string.hub_binge_connect_hint),
                actionLabel = stringResource(R.string.hub_binge_open),
                actionIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onAction = { context.openBinge() },
                onDismiss = onDismissHint,
            )
        BingeStatus.Connected ->
            HintCard(
                text = stringResource(R.string.hub_binge_connected_hint),
                actionLabel = stringResource(R.string.hub_binge_open),
                actionIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onAction = { context.openBinge() },
                onDismiss = onDismissHint,
            )
    }
}

/** Debug builds only, after everything else: the developer tools, one row each. */
@Composable
private fun DeveloperGroup(
    developerRows: List<DeveloperRow>,
    inset: Dp,
) {
    SectionHeader(title = stringResource(R.string.debug_group_developer))
    ItemGroup(
        title = null,
        rows =
            developerRows.map { row ->
                ListItem(
                    icon = row.icon,
                    label = stringResource(row.label),
                    detail = stringResource(row.detail),
                    onClick = row.onClick,
                )
            },
        modifier = Modifier.padding(start = inset, end = inset, bottom = inset),
    )
}

/** One row of the debug-only developer section; the label and detail are resources so a release build never names them. */
class DeveloperRow(
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val detail: Int,
    val onClick: () -> Unit,
)

/** Server gone or the dashboard not loaded (retry, or edit the connection), or the session rejected (reconnect). */
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
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_headline
                    else -> R.string.hub_unreachable_headline
                },
            ),
        message =
            stringResource(
                when (health) {
                    ConnectionHealth.Unauthorized -> R.string.hub_unauthorized_body
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_body
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_body
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
                if (health == ConnectionHealth.LocalNetworkDenied) {
                    val allow = rememberAllowLocalNetwork(onRetry)
                    BingeFilledButton(label = stringResource(allow.label), onClick = allow.run, modifier = Modifier.fillMaxWidth())
                    BingeOutlinedButton(
                        label = stringResource(R.string.settings_edit_connection),
                        onClick = onReconnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (retryable) {
                    BingeFilledButton(label = stringResource(R.string.hub_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
                    BingeOutlinedButton(
                        label = stringResource(R.string.settings_edit_connection),
                        onClick = onReconnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
