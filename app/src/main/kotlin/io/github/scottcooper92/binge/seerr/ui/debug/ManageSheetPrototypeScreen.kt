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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActionsContent
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSheetCallbacks
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSheetModel
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import kotlinx.coroutines.launch
import com.binge.designsystem.R as DesR

/**
 * Debug builds only: the request manage sheet's proposed layout beside the current one, over sample
 * requests covering each branch the sheet draws. Nothing here reaches a server — a tap names the
 * action it would take in a snackbar — and "Open as a sheet" shows the chosen version in a real one.
 */
@Composable
internal fun ManageSheetPrototypeScreen(onBack: () -> Unit) {
    var scenario by rememberSaveable { mutableStateOf(ManageSheetScenario.PendingModerator) }
    var proposed by rememberSaveable { mutableStateOf(true) }
    var blockTitle by rememberSaveable { mutableStateOf(false) }
    var asSheet by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val model = remember(scenario) { scenario.model() }
    val tapped = stringResource(R.string.proto_tapped)
    val callbacks = prototypeCallbacks { action -> scope.launch { snackbar.showSnackbar(tapped.format(action)) } }
    val body: @Composable () -> Unit = {
        SheetBody(proposed, model, callbacks, blockTitle) { blockTitle = it }
    }

    ScreenScaffold(
        title = stringResource(R.string.proto_manage_sheet_title),
        onBack = onBack,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.outerPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(padding.innerPadding()),
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
            VersionToggle(proposed = proposed, onChange = { proposed = it })
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

@Composable
private fun VersionToggle(
    proposed: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = resolvedContentInset()),
    ) {
        listOf(true to R.string.proto_version_proposed, false to R.string.proto_version_current).forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = proposed == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun SheetBody(
    proposed: Boolean,
    model: RequestSheetModel,
    callbacks: RequestSheetCallbacks,
    blockTitle: Boolean,
    onBlockTitleChange: (Boolean) -> Unit,
) {
    if (proposed) {
        RequestActionsPrototypeContent(model, callbacks, blockTitle, onBlockTitleChange)
    } else {
        RequestActionsContent(model, callbacks, blockTitle, onBlockTitleChange)
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
