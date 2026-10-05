package io.github.scottcooper92.binge.seerr.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActionsContent
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSheetCallbacks
import kotlinx.coroutines.launch
import com.binge.designsystem.R as DesR

/**
 * Debug builds only: the request manage sheet over sample requests covering each branch it draws.
 * Nothing here reaches a server — a tap names the action it would take in a snackbar — and "Open as
 * a sheet" shows it in a real one.
 */
@Composable
internal fun ManageSheetPrototypeScreen(onBack: () -> Unit) {
    var scenario by rememberSaveable { mutableStateOf(ManageSheetScenario.PendingModerator) }
    var blockTitle by rememberSaveable { mutableStateOf(false) }
    var asSheet by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val model = remember(scenario) { scenario.model() }
    val tapped = stringResource(R.string.proto_tapped)
    val callbacks = prototypeCallbacks { action -> scope.launch { snackbar.showSnackbar(tapped.format(action)) } }
    val body: @Composable () -> Unit = {
        RequestActionsContent(model, callbacks, blockTitle, onBlockTitleChange = { blockTitle = it })
    }

    BingeScreenScaffold(
        bar = ScreenBar.Small,
        title = stringResource(R.string.proto_manage_sheet_title),
        onBack = onBack,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.screenOuterPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(padding.screenInnerPadding()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            BingeFilterChipRow(
                items = ManageSheetScenario.entries.map { FilterChipItem(label = stringResource(it.labelRes)) },
                selectedIndex = scenario.ordinal,
                onSelect = {
                    scenario = ManageSheetScenario.entries[it]
                    blockTitle = false
                },
            )
            // The sheet's own surface and top corners, so the content reads as it would in the sheet.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = resolvedContentInset())
                        .clip(BingeShapes.HeroTop)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(top = dimensionResource(DesR.dimen.padding_l)),
            ) { body() }
            BingeTextButton(
                label = stringResource(R.string.proto_open_as_sheet),
                onClick = { asSheet = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (asSheet) {
        BingeBottomSheet(onDismissRequest = { asSheet = false }) { body() }
    }
}

/** Every action names itself in [onAction] rather than doing anything: this screen has no server. */
@Composable
private fun prototypeCallbacks(onAction: (String) -> Unit): RequestSheetCallbacks {
    val approve = stringResource(R.string.request_approve)
    val retry = stringResource(R.string.request_retry)
    val decline = stringResource(R.string.request_decline)
    val remove = stringResource(R.string.request_remove)
    val edit = stringResource(R.string.request_edit_title)
    val markAs = stringResource(R.string.media_mark_as)
    val deleteFiles = stringResource(R.string.media_delete_files)
    val clearData = stringResource(R.string.media_clear_data)
    return RequestSheetCallbacks(
        onApprove = { onAction(approve) },
        onRetry = { onAction(retry) },
        onDecline = { onAction(decline) },
        onRemove = { onAction(remove) },
        onEdit = { onAction(edit) },
        onMarkStatus = { onAction(markAs) },
        onDeleteFiles = { onAction(deleteFiles) },
        onClearData = { onAction(clearData) },
    )
}
