package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.tip.TipEvent
import com.cooper.binge.companion.seerr.tip.TipUiState
import com.cooper.binge.companion.seerr.ui.settings.server.TipSheet
import com.cooper.binge.companion.seerr.ui.state.DialogMessage
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.MessageDialogHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.binge.designsystem.R as DesR

/** What the This app page can do, as the rows ask for it. */
class ThisAppActions(
    val onBack: () -> Unit,
    val onToggleShakeToReport: (Boolean) -> Unit,
    val onToggleShareUsageData: (Boolean) -> Unit,
    val onToggleSendCrashReports: (Boolean) -> Unit,
    val onOpenLicences: () -> Unit = {},
)

/**
 * This app: Binge, bug reporting, what the app may report on its own, the tip jar, then About. It is about the app and not the
 * server, so every viewer has it, where Settings is the administrator's.
 *
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 * @param tip the tip jar: its row shows only once every step is priced. [tipEvents] says how a tip ended.
 */
@Composable
fun ThisAppScreen(
    state: ThisAppUiState,
    actions: ThisAppActions,
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
        title = stringResource(R.string.hub_section_this_app),
        onBack = actions.onBack.takeIf { showBack },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when (state) {
                ThisAppUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is ThisAppUiState.Ready -> ThisAppContent(state.app, actions, tip, onGiveTip, contentPadding = inner)
            }
        }
    }
}

@Composable
private fun ThisAppContent(
    app: AppSettings,
    actions: ThisAppActions,
    tip: TipUiState,
    onGiveTip: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
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
        ItemGroup(
            title = null,
            rows =
                appRows(
                    app,
                    actions.onToggleShakeToReport,
                    actions.onToggleShareUsageData,
                    actions.onToggleSendCrashReports,
                    onOpenTip = openTip,
                ),
            modifier = Modifier.padding(resolvedContentPadding()),
        )
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        ItemGroup(
            title = stringResource(R.string.settings_group_about),
            rows = aboutRows(actions.onOpenLicences),
            modifier = Modifier.padding(resolvedContentPadding()),
        )
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    }
}
