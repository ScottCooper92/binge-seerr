package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvDetailAction
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail

/** The most the action row shows before Manage. */
private const val MAX_LEADS = 2

/**
 * What a viewer may do to a request from the TV page, in the order Manage lists them: the decisions first, the destructive
 * ones last, block (reversible) before remove (irreversible). [sheet] is what the action opens over the page, if anything.
 */
internal enum class TvRequestAction(
    val sheet: DetailSheet? = null,
    val isDecision: Boolean = false,
    val destructive: Boolean = false,
) {
    Retry(isDecision = true),
    Approve(isDecision = true),
    Decline(isDecision = true),
    OpenInMediaServer,
    Edit(sheet = DetailSheet.Edit),
    MarkAs(sheet = DetailSheet.MarkAs),
    Report(sheet = DetailSheet.Report),
    OpenInBinge,
    Block(sheet = DetailSheet.Block, destructive = true),
    Remove(sheet = DetailSheet.Remove, destructive = true),
}

/**
 * The actions this request offers on TV. The page is a deliberate subset of the phone's (#1036): it moderates, edits the
 * request, marks the title's state, opens the title in the media server's TV app where one is installed, and hands an
 * issue report to the phone. A request's tags, its requester and the title's other requests stay on the phone.
 */
internal fun availableRequestActions(
    detail: RequestDetail,
    canEdit: Boolean,
    canOpenInMediaServer: Boolean,
    canOpenInBinge: Boolean,
): List<TvRequestAction> {
    val allowed = detail.actions
    return TvRequestAction.entries.filter { action ->
        when (action) {
            TvRequestAction.Retry -> allowed.canRetry
            TvRequestAction.Approve -> allowed.canApprove
            TvRequestAction.Decline -> allowed.canDecline
            TvRequestAction.OpenInMediaServer -> canOpenInMediaServer
            TvRequestAction.Edit -> detail.canEdit && canEdit
            TvRequestAction.MarkAs -> detail.media?.let { it.canSetStatus && it.instances.isNotEmpty() } == true
            TvRequestAction.Report -> detail.canReportIssue && detail.mediaId != null
            TvRequestAction.OpenInBinge -> canOpenInBinge
            TvRequestAction.Block -> allowed.canBlock
            TvRequestAction.Remove -> allowed.canRemove
        }
    }
}

/** The action row's buttons before Manage, and what Manage lists. */
internal data class TvRequestActionSplit(
    val leads: List<TvRequestAction>,
    val managed: List<TvRequestAction>,
)

/**
 * At most two actions lead the row. A request awaiting a decision leads with it: Approve and Decline, or Retry. Any other
 * leads with opening the title in the media server and marking its state, or reporting an issue where it cannot be
 * marked. Everything else is in Manage, in [TvRequestAction]'s order, so the destructive ones are last; a single action
 * left over joins the row instead where there is room, unless it is destructive.
 */
internal fun List<TvRequestAction>.splitLeads(): TvRequestActionSplit {
    val decisions = filter { it.isDecision }
    val leads =
        if (decisions.isNotEmpty()) {
            decisions
        } else {
            listOfNotNull(
                TvRequestAction.OpenInMediaServer.takeIf { it in this },
                TvRequestAction.MarkAs.takeIf { it in this } ?: TvRequestAction.Report.takeIf { it in this },
            )
        }.take(MAX_LEADS)
    val managed = this - leads.toSet()
    // A Manage holding one thing is a menu for nothing: it joins the row, unless it is destructive or the row is full.
    val lone = managed.singleOrNull()?.takeIf { !it.destructive && leads.size < MAX_LEADS }
    return if (lone != null) TvRequestActionSplit(leads + lone, emptyList()) else TvRequestActionSplit(leads, managed)
}

/** One action as the page offers it, on a button in the row or a row in the Manage sheet. */
internal class TvRequestActionItem(
    val action: TvRequestAction,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** The row's buttons, the Manage sheet's rows, and whether Manage is offered. */
internal class TvRequestActionPlan(
    val row: List<TvDetailAction>,
    val managed: List<TvRequestActionItem>,
)

/**
 * The row and Manage for this request. A lead that opens a sheet carries the sheet's focus requester, so the sheet hands
 * focus back to it; a sheet opened from Manage hands it back to Manage, [manageFocus].
 */
@Composable
internal fun requestDetailActions(
    detail: RequestDetail,
    actions: TvRequestDetailActions,
    onOpenSheet: (DetailSheet) -> Unit,
    onOpenManage: () -> Unit,
    sheetFocus: Map<DetailSheet, FocusRequester>,
    manageFocus: FocusRequester,
): TvRequestActionPlan {
    val split =
        availableRequestActions(
            detail = detail,
            canEdit = actions.edit != null,
            canOpenInMediaServer = detail.mediaServerName != null && actions.onOpenInMediaServer != null,
            canOpenInBinge = actions.onOpenInBinge != null,
        ).splitLeads()
    val items = (split.leads + split.managed).associateWith { actionItem(it, detail, actions, onOpenSheet) }
    val row =
        split.leads.map { action ->
            val item = items.getValue(action)
            TvDetailAction(
                label = item.label,
                icon = item.icon,
                onClick = item.onClick,
                isPrimary = action == TvRequestAction.Approve || action == TvRequestAction.Retry,
                showLabel = true,
                focusRequester = action.sheet?.let(sheetFocus::getValue),
            )
        }
    val manage =
        TvDetailAction(
            label = stringResource(R.string.tv_detail_manage),
            icon = Icons.Filled.Tune,
            onClick = onOpenManage,
            showLabel = true,
            focusRequester = manageFocus,
        ).takeIf { split.managed.isNotEmpty() }
    return TvRequestActionPlan(row = row + listOfNotNull(manage), managed = split.managed.map(items::getValue))
}

@Composable
private fun actionItem(
    action: TvRequestAction,
    detail: RequestDetail,
    actions: TvRequestDetailActions,
    onOpenSheet: (DetailSheet) -> Unit,
): TvRequestActionItem {
    val openSheet = { sheet: DetailSheet -> { onOpenSheet(sheet) } }
    return when (action) {
        TvRequestAction.Retry ->
            TvRequestActionItem(
                action,
                stringResource(R.string.hub_retry),
                Icons.Filled.Refresh,
                actions.onRetryRequest,
            )
        TvRequestAction.Approve ->
            TvRequestActionItem(
                action,
                stringResource(R.string.tv_detail_approve),
                Icons.Filled.Check,
                actions.onApprove,
            )
        TvRequestAction.Decline ->
            TvRequestActionItem(action, stringResource(R.string.tv_detail_decline), Icons.Filled.Close) { actions.onDecline(false) }
        TvRequestAction.OpenInMediaServer ->
            TvRequestActionItem(
                action,
                stringResource(R.string.open_in_named, detail.mediaServerName.orEmpty()),
                Icons.Filled.PlayArrow,
                actions.onOpenInMediaServer ?: {},
            )
        TvRequestAction.Edit ->
            TvRequestActionItem(action, stringResource(R.string.tv_detail_edit), Icons.Filled.Edit) {
                actions.edit?.onStart?.invoke()
                onOpenSheet(DetailSheet.Edit)
            }
        TvRequestAction.MarkAs ->
            TvRequestActionItem(action, stringResource(R.string.media_mark_as), Icons.Filled.Done, openSheet(DetailSheet.MarkAs))
        TvRequestAction.Report ->
            TvRequestActionItem(
                action,
                stringResource(R.string.issue_report_title),
                Icons.Filled.ReportProblem,
                openSheet(DetailSheet.Report),
            )
        TvRequestAction.OpenInBinge ->
            TvRequestActionItem(
                action,
                stringResource(R.string.request_open_binge),
                Icons.AutoMirrored.Filled.OpenInNew,
                actions.onOpenInBinge ?: {},
            )
        TvRequestAction.Block ->
            TvRequestActionItem(
                action,
                stringResource(R.string.tv_detail_block),
                Icons.Filled.Block,
                openSheet(DetailSheet.Block),
            )
        TvRequestAction.Remove ->
            TvRequestActionItem(action, stringResource(R.string.tv_detail_remove), Icons.Filled.Delete, openSheet(DetailSheet.Remove))
    }
}
