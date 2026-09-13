package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.requests.statusChip
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheet
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetBody
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetConfirm
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetRow
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetStepFocus
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetTitle

/** The actions that take a second step before they land: the ones that delete a request or block a title. */
private enum class Pending { DeclineAndBlock, Remove, RemoveAndBlock }

/** Everything the sheet can do to its request; the caller closes the sheet after each. */
internal class TvRequestSheetActions(
    val onApprove: () -> Unit,
    val onRetry: () -> Unit,
    val onDecline: (block: Boolean) -> Unit,
    val onRemove: (block: Boolean) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * A request's moderation on the end-edge sheet: approve or retry, decline, and remove, each with its
 * block-the-title twin where the viewer may block. The rows that destroy something confirm on a second
 * step; a plain decline does not, since the server keeps the request and it can be approved after.
 */
@Composable
internal fun TvRequestActionsSheet(
    item: RequestItem,
    actions: RequestActions,
    sheetActions: TvRequestSheetActions,
    modifier: Modifier = Modifier,
) {
    var pending by rememberSaveable { mutableStateOf<Pending?>(null) }
    TvActionSheet(onDismiss = sheetActions.onDismiss, modifier = modifier) { entryFocus ->
        val step = pending
        if (step != null) {
            TvPendingStep(step = step, sheetActions = sheetActions, entryFocus = entryFocus, onCancel = { pending = null })
            return@TvActionSheet
        }
        TvActionSheetTitle(item.title ?: stringResource(item.mediaType.labelRes()))
        TvActionSheetBody(
            listOfNotNull(item.requestedBy, stringResource(item.statusChip().labelRes))
                .joinToString(stringResource(R.string.hub_meta_separator)),
        )
        val rows =
            buildList {
                if (actions.canRetry) add(TvSheetChoice(R.string.request_retry, onClick = sheetActions.onRetry))
                if (actions.canApprove) add(TvSheetChoice(R.string.request_approve, onClick = sheetActions.onApprove))
                if (actions.canDecline) add(TvSheetChoice(R.string.request_decline) { sheetActions.onDecline(false) })
                if (actions.canDecline && actions.canBlock) {
                    add(TvSheetChoice(R.string.request_decline_and_block, destructive = true) { pending = Pending.DeclineAndBlock })
                }
                if (actions.canRemove) add(TvSheetChoice(R.string.request_remove, destructive = true) { pending = Pending.Remove })
                if (actions.canRemove && actions.canBlock) {
                    add(TvSheetChoice(R.string.request_remove_and_block, destructive = true) { pending = Pending.RemoveAndBlock })
                }
            }
        rows.forEachIndexed { index, choice ->
            TvActionSheetRow(
                label = stringResource(choice.labelRes),
                onClick = choice.onClick,
                destructive = choice.destructive,
                modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
        TvActionSheetStepFocus(entryFocus)
    }
}

private class TvSheetChoice(
    val labelRes: Int,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.TvPendingStep(
    step: Pending,
    sheetActions: TvRequestSheetActions,
    entryFocus: FocusRequester,
    onCancel: () -> Unit,
) {
    when (step) {
        Pending.DeclineAndBlock ->
            TvActionSheetConfirm(
                title = stringResource(R.string.request_decline_block_confirm_title),
                message = stringResource(R.string.request_decline_block_confirm_message),
                confirmLabel = stringResource(R.string.request_decline_and_block),
                onConfirm = { sheetActions.onDecline(true) },
                onCancel = onCancel,
                entryFocus = entryFocus,
            )
        Pending.Remove ->
            TvActionSheetConfirm(
                title = stringResource(R.string.request_remove_confirm_title),
                message = stringResource(R.string.request_remove_confirm_message),
                confirmLabel = stringResource(R.string.request_remove),
                onConfirm = { sheetActions.onRemove(false) },
                onCancel = onCancel,
                entryFocus = entryFocus,
            )
        Pending.RemoveAndBlock ->
            TvActionSheetConfirm(
                title = stringResource(R.string.request_remove_confirm_title),
                message = stringResource(R.string.request_remove_block_confirm_message),
                confirmLabel = stringResource(R.string.request_remove_and_block),
                onConfirm = { sheetActions.onRemove(true) },
                onCancel = onCancel,
                entryFocus = entryFocus,
            )
    }
}
