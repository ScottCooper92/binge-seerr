package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.HintCard
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.DisconnectButton
import io.github.scottcooper92.binge.seerr.ui.rememberAllowLocalNetwork
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

class HubActions(
    val onOpenSection: (HubSection) -> Unit,
    val onOpenAccount: (userId: Int) -> Unit,
    val onOpenRequest: (requestId: Int) -> Unit,
    val onRetry: () -> Unit,
    /** The sign-in form on the saved server, to correct an address that no longer answers. */
    val onReconnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onDismissBingeHint: () -> Unit,
    /** The dashboard's pull: re-reads the counts and the downloads. */
    val onRefresh: () -> Unit,
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
 * @param pullState a still frame's resting pull; null remembers M3's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    state: HubUiState,
    actions: HubActions,
    selectedSection: HubSection? = null,
    admitsUnverifiedCallers: Boolean = false,
    pullState: PullToRefreshState? = null,
) {
    val ready = state as? HubUiState.Ready
    // The view model holds a problem through the re-check meant to clear it (#873), so this is the problem to name.
    val health = if (state is HubUiState.Error) state.health else ready?.health
    val rechecking = (state as? HubUiState.Error)?.rechecking ?: ready?.rechecking ?: false
    val allow = rememberAllowLocalNetwork { if (health == ConnectionHealth.LocalNetworkDenied) actions.onRetry() }
    // The account sits in the bar while the dashboard is up (#1325): the photo or initials, ringed by what is left of the quota.
    val account = ready?.overview?.account?.takeUnless { ready.health.isProblem() }
    BingeScreenScaffold(
        bar = ScreenBar.Small,
        title = ready?.server?.title ?: stringResource(R.string.companion_name),
        actions = {
            account?.let {
                HubAccountAction(account = it, quota = ready.overview.quota, onClick = { actions.onOpenAccount(it.id) })
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when {
                // A rejected session is the app's to answer, by going to sign-in (#810): nothing to offer here meanwhile.
                ready?.health == ConnectionHealth.Unauthorized -> LoadingScreen(Modifier.padding(inner))
                state is HubUiState.Error ->
                    ConnectionProblem(
                        state.health,
                        allow,
                        actions.onRetry,
                        rechecking,
                        actions.onReconnect,
                        actions.onDisconnect,
                        Modifier.padding(inner),
                    )
                ready == null -> LoadingScreen(Modifier.padding(inner))
                ready.health.isProblem() ->
                    ConnectionProblem(
                        ready.health,
                        allow,
                        actions.onRetry,
                        rechecking,
                        actions.onReconnect,
                        actions.onDisconnect,
                        Modifier.padding(inner),
                    )
                // Only the dashboard pulls: a problem page has its own retry, which re-checks the server as a whole.
                else ->
                    BingePullToRefresh(
                        isRefreshing = ready.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = inner.calculateTopPadding(),
                        state = pullState ?: rememberPullToRefreshState(),
                    ) { Dashboard(ready, actions, selectedSection, admitsUnverifiedCallers, contentPadding = inner) }
            }
        }
    }
}

/**
 * Whether the hub shows the server's problem in place of its dashboard. Not [ConnectionHealth.Unauthorized]: a rejection
 * the server confirms takes the app to sign-in (#810), and one it doesn't was a stray answer, not a problem to show.
 */
internal fun ConnectionHealth.isProblem(): Boolean =
    this == ConnectionHealth.Unreachable ||
        this == ConnectionHealth.LocalNetworkDenied ||
        this == ConnectionHealth.CouldNotLoad

@Composable
private fun Dashboard(
    state: HubUiState.Ready,
    actions: HubActions,
    selectedSection: HubSection?,
    admitsUnverifiedCallers: Boolean,
    contentPadding: PaddingValues,
) {
    val inset = resolvedContentInset()
    // Each side from the pane it sits in: the edge shared with the section beside it takes the narrow inner inset (#819).
    val sides = resolvedContentPadding()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        if (admitsUnverifiedCallers) {
            HintCard(
                text = stringResource(R.string.hub_unverified_callers),
                icon = Icons.Filled.Warning,
                modifier = Modifier.padding(sides).padding(bottom = dimensionResource(DesR.dimen.padding_m)),
            )
        }
        ServerCard(server = state.server, overview = state.overview, sides = sides)
        if (state.downloading.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.hub_downloading_now, state.downloading.size))
            DownloadingStrip(state.downloading, onClick = { actions.onOpenRequest(it.requestId) }, sides = sides)
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
            modifier = Modifier.padding(sides),
        )
        Column(
            modifier = Modifier.padding(resolvedContentPadding(vertical = inset)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            BingeTile(state.bingeStatus, state.bingeHintDismissed, actions.onDismissBingeHint)
            DisconnectButton(actions.onDisconnect)
        }
        if (actions.developerRows.isNotEmpty()) DeveloperGroup(actions.developerRows, resolvedContentPadding(bottom = inset))
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
    padding: PaddingValues,
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
        modifier = Modifier.padding(padding),
    )
}

/** One row of the debug-only developer section; the label and detail are resources so a release build never names them. */
class DeveloperRow(
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val detail: Int,
    val onClick: () -> Unit,
)
