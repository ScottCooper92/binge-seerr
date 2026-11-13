package com.cooper.binge.companion.seerr.ui.settings

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
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.demo.isDemoOrigin
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import com.cooper.binge.companion.seerr.tip.TipEvent
import com.cooper.binge.companion.seerr.tip.TipUiState
import com.cooper.binge.companion.seerr.ui.DisconnectButton
import com.cooper.binge.companion.seerr.ui.handoff.rememberScanTvCode
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.settings.server.TipSheet
import com.cooper.binge.companion.seerr.ui.state.DialogMessage
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.MessageDialogHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    val onToggleSignal: (NotificationSignal, Boolean) -> Unit,
    val onNotificationAccessChanged: () -> Unit,
    val onToggleShakeToReport: (Boolean) -> Unit,
    val onToggleShareUsageData: (Boolean) -> Unit,
    val onToggleSendCrashReports: (Boolean) -> Unit,
    val onDisconnect: () -> Unit,
    val onOpenLicences: () -> Unit = {},
)

/**
 * Settings: the connection and a way to edit it, then the admin's view of the server — the general
 * settings open their own page for editing; the other groups read until their phase.
 *
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 * @param tip the tip jar: its row shows only once every step is priced. [tipEvents] says how a tip ended.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    showBack: Boolean = true,
    tip: TipUiState = TipUiState.Unavailable,
    tipEvents: Flow<TipEvent> = emptyFlow(),
    onGiveTip: (Int) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DialogMessage?>(null) }
    TipResults(tipEvents, snackbarHostState, onFailed = { dialog = it })
    MessageDialogHost(dialog, onDismiss = { dialog = null })
    BingeScreenScaffold(
        bar = ScreenBar.Small,
        title = stringResource(R.string.hub_section_settings),
        onBack = actions.onBack.takeIf { showBack },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when (state) {
                SettingsUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is SettingsUiState.Ready -> SettingsContent(state, actions, tip, onGiveTip, contentPadding = inner)
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Ready,
    actions: SettingsActions,
    tip: TipUiState,
    onGiveTip: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val config = state.config
    var tipOpen by remember { mutableStateOf(false) }
    val prices = (tip as? TipUiState.Ready)?.prices
    if (tipOpen && prices != null) {
        TipSheet(
            prices = prices,
            onGive = {
                tipOpen = false
                onGiveTip(it)
            },
            onDismiss = { tipOpen = false },
        )
    }
    val openTip: (() -> Unit)? = if (prices != null) ({ tipOpen = true }) else null
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        if (state.connection.baseUrl.isDemoOrigin()) DemoNote()
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Filled.Tv,
                        label = stringResource(R.string.hub_scan_tv_code),
                        detail = stringResource(R.string.hub_scan_tv_code_detail),
                        onClick = rememberScanTvCode(),
                    ),
                ),
            modifier = Modifier.padding(resolvedContentPadding()),
        )
        if (state.pending) PendingGroups()
        // The server's settings as the web client's Settings menu lists them, one row per section; then this app's own.
        config?.let {
            Group(
                stringResource(R.string.settings_group_server_settings),
                serverSectionRows(it, state.server, actions.onOpenPage),
            )
        }
        Group(
            stringResource(R.string.settings_group_connection),
            // About is a server section above when this viewer can read the server's settings, so it isn't said twice.
            connectionRows(state.connection, state.server, actions.onEditConnection, actions.onOpenPage, showAbout = config == null),
        )
        state.notifications?.let { Group(stringResource(R.string.settings_group_notify_me), notificationRows(it, actions, state.now)) }
        state.app?.let {
            Group(
                stringResource(R.string.settings_group_app),
                appRows(
                    it,
                    actions.onToggleShakeToReport,
                    actions.onToggleShareUsageData,
                    actions.onToggleSendCrashReports,
                    onOpenTip = openTip,
                ),
            )
        }
        Group(stringResource(R.string.settings_group_about), aboutRows(actions.onOpenLicences))
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        DisconnectButton(
            actions.onDisconnect,
            modifier = Modifier.padding(resolvedContentPadding()),
            demo = state.connection.baseUrl.isDemoOrigin(),
        )
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    }
}

/** Says the demo is the demo, the one thing a visitor to its full admin console cannot otherwise tell. */
@Composable
internal fun DemoNote() {
    PageIntro(stringResource(R.string.settings_demo_note), Modifier.padding(resolvedContentPadding()))
}

/** One plate where the admin groups will land, so the groups below make room once rather than jumping as each arrives. */
@Composable
private fun PendingGroups() {
    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    SkeletonPlate(
        modifier =
            Modifier
                .padding(resolvedContentPadding())
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.settings_pending_groups_height)),
        shape = BingeShapes.Large,
    )
}

/** A titled group with the screen's spacing above it; skipped when it has no rows. */
@Composable
private fun Group(
    title: String,
    rows: List<ListItem>,
) {
    if (rows.isEmpty()) return
    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    ItemGroup(title = title, rows = rows, modifier = Modifier.padding(resolvedContentPadding()))
}
