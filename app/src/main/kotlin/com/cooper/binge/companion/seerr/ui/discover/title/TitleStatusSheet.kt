package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.ActionSheetGroup
import com.cooper.binge.companion.seerr.ui.state.actionItem
import com.cooper.binge.companion.seerr.ui.state.externalItem
import com.binge.designsystem.R as DesR

/**
 * What the primary opens where no request of the viewer's is on file: the title's status, Watch once the title is in the library,
 * and otherwise a line saying why there is nothing to do. The primary is never dead, so this is what a tap shows.
 */
@Composable
internal fun TitleStatusSheet(
    state: TitleState,
    watchUrl: String?,
    mediaServerName: String?,
    canRequest: Boolean,
    onWatch: (String) -> Unit,
    onDismiss: () -> Unit,
    onUnblock: (() -> Unit)? = null,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        TitleStatusSheetContent(
            state = state,
            watchUrl = watchUrl,
            mediaServerName = mediaServerName,
            canRequest = canRequest,
            onWatch = { url ->
                onDismiss()
                onWatch(url)
            },
            onUnblock = onUnblock,
        )
    }
}

/** [TitleStatusSheet]'s body without its window, for a frame. */
@Composable
internal fun TitleStatusSheetContent(
    state: TitleState,
    watchUrl: String?,
    mediaServerName: String?,
    canRequest: Boolean,
    onWatch: (String) -> Unit,
    modifier: Modifier = Modifier,
    onUnblock: (() -> Unit)? = null,
) {
    val watch = watchUrl?.takeIf { state.isInLibrary() }
    if (watch != null) {
        val label = mediaServerName?.let { stringResource(R.string.title_sheet_watch_on, it) } ?: stringResource(R.string.title_sheet_watch)
        ActionSheetGroup(
            rows = listOf(externalItem(Icons.Filled.PlayArrow, label) { onWatch(watch) }),
            modifier = modifier,
            title = stringResource(state.labelRes()),
        )
    } else {
        Column(
            modifier
                .padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                ).padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        ) {
            Text(stringResource(state.labelRes()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(state.noteRes(canRequest)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state == TitleState.Blocklisted && onUnblock != null) {
                ItemGroup(
                    title = null,
                    rows = listOf(actionItem(Icons.Filled.LockOpen, stringResource(R.string.blocklist_unblock), onClick = onUnblock)),
                    modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_m)),
                )
            }
        }
    }
}

@StringRes
private fun TitleState.noteRes(canRequest: Boolean) =
    when (this) {
        TitleState.Request -> if (canRequest) R.string.title_sheet_note_nothing_to_request else R.string.title_sheet_note_cannot_request
        TitleState.Requested -> R.string.title_sheet_note_other_request
        TitleState.Processing -> R.string.title_sheet_note_processing
        TitleState.PartiallyAvailable, TitleState.Available -> R.string.title_sheet_note_in_library
        TitleState.Blocklisted -> R.string.title_sheet_note_blocklisted
    }
