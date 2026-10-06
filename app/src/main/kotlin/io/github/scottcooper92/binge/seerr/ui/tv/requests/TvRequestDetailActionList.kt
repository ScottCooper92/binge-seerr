package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvDetailAction
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions

/** The action row: what this viewer may do to the request, in the order a manager reaches for them. */
@Composable
internal fun requestDetailActions(
    allowed: RequestActions,
    actions: TvRequestDetailActions,
    onConfirm: (DetailConfirm) -> Unit,
    removeFocus: FocusRequester,
    blockFocus: FocusRequester,
): List<TvDetailAction> {
    val retryLabel = stringResource(R.string.hub_retry)
    val approveLabel = stringResource(R.string.tv_detail_approve)
    val declineLabel = stringResource(R.string.tv_detail_decline)
    val removeLabel = stringResource(R.string.tv_detail_remove)
    val blockLabel = stringResource(R.string.tv_detail_block)
    val openLabel = stringResource(R.string.request_open_binge)
    return buildList {
        if (allowed.canRetry) add(TvDetailAction(retryLabel, Icons.Filled.Refresh, onClick = actions.onRetryRequest, isPrimary = true))
        if (allowed.canApprove) add(TvDetailAction(approveLabel, Icons.Filled.Check, onClick = actions.onApprove, isPrimary = true))
        if (allowed.canDecline) {
            add(TvDetailAction(declineLabel, Icons.Filled.Close, onClick = { actions.onDecline(false) }, showLabel = true))
        }
        if (allowed.canRemove) {
            add(
                TvDetailAction(
                    removeLabel,
                    Icons.Filled.Delete,
                    onClick = { onConfirm(DetailConfirm.Remove) },
                    showLabel = true,
                    focusRequester = removeFocus,
                ),
            )
        }
        if (allowed.canBlock) {
            add(
                TvDetailAction(
                    blockLabel,
                    Icons.Filled.Block,
                    onClick = { onConfirm(DetailConfirm.Block) },
                    showLabel = true,
                    focusRequester = blockFocus,
                ),
            )
        }
        actions.onOpenInBinge?.let { add(TvDetailAction(openLabel, Icons.AutoMirrored.Filled.OpenInNew, onClick = it)) }
    }
}
