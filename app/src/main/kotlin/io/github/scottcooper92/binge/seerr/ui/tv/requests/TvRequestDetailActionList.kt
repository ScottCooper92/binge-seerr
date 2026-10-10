package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvDetailAction
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.canOpenUser

/**
 * The action row: what this viewer may do to the request, in the order a manager reaches for them.
 *
 * The TV page is a deliberate subset of the phone's (#1036). It moderates, marks the title's state and reports an issue.
 * Editing the seasons and the destination, opening the requester's page and the title's other requests stay on the
 * phone: each needs a picker or a page this surface has no room for. The details row says so where they apply
 * ([phoneOnlyCard]), so a missing control reads as a decision rather than a gap.
 */
@Composable
internal fun requestDetailActions(
    detail: RequestDetail,
    actions: TvRequestDetailActions,
    onOpenSheet: (DetailSheet) -> Unit,
    sheetFocus: Map<DetailSheet, FocusRequester>,
): List<TvDetailAction> {
    val allowed = detail.actions
    val retryLabel = stringResource(R.string.hub_retry)
    val approveLabel = stringResource(R.string.tv_detail_approve)
    val declineLabel = stringResource(R.string.tv_detail_decline)
    val removeLabel = stringResource(R.string.tv_detail_remove)
    val blockLabel = stringResource(R.string.tv_detail_block)
    val openLabel = stringResource(R.string.request_open_binge)
    val markLabel = stringResource(R.string.media_mark_as)
    val reportLabel = stringResource(R.string.issue_report_title)
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
                    onClick = { onOpenSheet(DetailSheet.Remove) },
                    showLabel = true,
                    focusRequester = sheetFocus.getValue(DetailSheet.Remove),
                ),
            )
        }
        if (allowed.canBlock) {
            add(
                TvDetailAction(
                    blockLabel,
                    Icons.Filled.Block,
                    onClick = { onOpenSheet(DetailSheet.Block) },
                    showLabel = true,
                    focusRequester = sheetFocus.getValue(DetailSheet.Block),
                ),
            )
        }
        if (detail.media?.let { it.canSetStatus && it.instances.isNotEmpty() } == true) {
            add(sheetAction(markLabel, Icons.Filled.Done, DetailSheet.MarkAs, onOpenSheet, sheetFocus))
        }
        if (detail.canReportIssue && detail.mediaId != null) {
            add(sheetAction(reportLabel, Icons.Filled.ReportProblem, DetailSheet.Report, onOpenSheet, sheetFocus))
        }
        actions.onOpenInBinge?.let { add(TvDetailAction(openLabel, Icons.AutoMirrored.Filled.OpenInNew, onClick = it)) }
    }
}

private fun sheetAction(
    label: String,
    icon: ImageVector,
    sheet: DetailSheet,
    onOpenSheet: (DetailSheet) -> Unit,
    sheetFocus: Map<DetailSheet, FocusRequester>,
) = TvDetailAction(label, icon, onClick = { onOpenSheet(sheet) }, showLabel = true, focusRequester = sheetFocus.getValue(sheet))

/**
 * The card that names what this request has on the phone and not here: an edit, its requester's page, the title's
 * other requests. Null when none of them applies.
 */
@Composable
internal fun phoneOnlyCard(detail: RequestDetail): TvInfoCardItem? {
    val parts =
        listOfNotNull(
            stringResource(R.string.tv_detail_phone_edit).takeIf { detail.canEdit },
            stringResource(R.string.tv_detail_phone_requester).takeIf {
                canOpenUser(detail.item.requestedById, detail.viewerId, detail.canManageUsers)
            },
            stringResource(R.string.tv_detail_phone_siblings).takeIf { detail.siblings.isNotEmpty() },
        )
    if (parts.isEmpty()) return null
    return TvInfoCardItem(
        stringResource(R.string.tv_detail_info_phone_only),
        parts.joinToString(stringResource(R.string.hub_meta_separator)),
    )
}
