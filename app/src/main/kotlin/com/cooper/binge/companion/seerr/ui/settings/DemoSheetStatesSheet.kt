package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.demo.DemoSheetState
import com.cooper.binge.companion.seerr.ui.state.ActionSheetGroup

/** The demo's "Sheet states" list in a sheet: one row per state of the request sheet, each opening the title that shows it. */
@Composable
internal fun DemoSheetStatesSheet(
    onDismiss: () -> Unit,
    onOpenTitle: (DemoSheetState) -> Unit,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        DemoSheetStatesContent(onOpenTitle = onOpenTitle)
    }
}

/** The rows of [DemoSheetStatesSheet], on their own so a test or a frame can render them without a modal window. */
@Composable
internal fun DemoSheetStatesContent(
    onOpenTitle: (DemoSheetState) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionSheetGroup(
        rows =
            DemoSheetState.entries.map { state ->
                ListItem(
                    icon = if (state.isMovie) Icons.Filled.Movie else Icons.Filled.Tv,
                    label = stringResource(state.labelRes()),
                    detail = state.title,
                    onClick = { onOpenTitle(state) },
                )
            },
        modifier = modifier.verticalScroll(rememberScrollState()),
    )
}

private fun DemoSheetState.labelRes(): Int =
    when (this) {
        DemoSheetState.NotRequested -> R.string.demo_state_not_requested
        DemoSheetState.PendingOthers -> R.string.demo_state_pending_others
        DemoSheetState.PendingOwn -> R.string.demo_state_pending_own
        DemoSheetState.ApprovedWaiting -> R.string.demo_state_approved_waiting
        DemoSheetState.Downloading -> R.string.demo_state_downloading
        DemoSheetState.QueuedAndPaused -> R.string.demo_state_queued_paused
        DemoSheetState.PartlyAvailable -> R.string.demo_state_partly_available
        DemoSheetState.AvailableWithWatch -> R.string.demo_state_available_watch
        DemoSheetState.AvailableWithPending4k -> R.string.demo_state_available_4k
        DemoSheetState.SeveralRequests -> R.string.demo_state_several_requests
        DemoSheetState.Declined -> R.string.demo_state_declined
        DemoSheetState.Failed -> R.string.demo_state_failed
        DemoSheetState.OpenIssue -> R.string.demo_state_open_issue
    }
