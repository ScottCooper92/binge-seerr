package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.tv.component.TvConfirmDialog
import com.binge.designsystem.tv.component.TvInfoCard
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetNav
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetRows
import com.binge.designsystem.tv.component.TvSideSheetStack
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.binge.designsystem.tv.component.TvSwitch
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.binge.designsystem.tv.R as TvR

internal const val PAGE_SETTINGS = "settings"
internal const val PAGE_BUG_REPORT = "bug-report"
private const val PAGE_GROUP_PREFIX = "group:"
private const val KEY_CLOSE = "close"
private const val KEY_SUMMARY = "summary"
private const val KEY_CODE = "code"

/** The page a group opens, named by its title: a group appears or goes as the hub reads the server, so an index would shift. */
internal fun groupPage(title: String): String = PAGE_GROUP_PREFIX + title

/**
 * Settings as a stack of side-sheet pages, like Jellyfin's TV preferences. The first page leads with the server at a
 * glance, lists the groups, and ends with Disconnect, which asks first. Each group opens a page: its rows to press first,
 * and what it only reads out after them as one card. Back goes up a page, and from the first closes the sheet through
 * [onDismissRequest]. The sheet runs to the screen's bottom edge, and each page carries the overscan inside its scroll.
 *
 * [initialPage] opens the sheet on a page other than the first, for a frame.
 */
@Composable
internal fun TvSettingsSheet(
    state: SettingsUiState,
    actions: TvSettingsActions,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    jobs: JobsUiState = JobsUiState.Loading,
    hub: HubUiState? = null,
    initialPage: String = PAGE_SETTINGS,
) {
    val ready = state as? SettingsUiState.Ready
    // Held for as long as the sheet is open, so a job pressed on Maintenance still says what came of it on a return.
    val presses = remember { TvJobPresses() }
    TvSideSheetStack(root = initialPage, onDismissRequest = onDismissRequest, modifier = modifier, toBottomEdge = true) { nav ->
        when {
            ready == null -> LoadingPage(nav)
            nav.page.agentOrNull() != null && actions.agents != null ->
                actions.agents.page(this, checkNotNull(nav.page.agentOrNull()), nav)
            nav.page == PAGE_BUG_REPORT && ready.app != null -> BugReportPage(nav, ready.app)
            else -> {
                val tvJobs = TvJobs(jobs, actions.jobs, presses)
                val agents =
                    actions.agents?.opening { agent -> nav.push(agentPage(agent), openedBy = "$KEY_AGENT_PREFIX${agent.segment}") }
                val groups =
                    tvSettingGroups(ready, actions, tvJobs, onShowBugReport = { nav.push(PAGE_BUG_REPORT, KEY_REPORT_BUG) }, agents)
                val group = groups.firstOrNull { groupPage(it.page.title) == nav.page }
                if (group != null) {
                    GroupPage(nav, group.page, actions.household)
                } else {
                    RootPage(nav, tvSettingsSummary(ready, hub), groups, actions.onDisconnect)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.LoadingPage(nav: TvSideSheetNav) {
    TvSideSheetTitle(stringResource(R.string.hub_section_settings))
    TvSideSheetBody(stringResource(R.string.tv_loading))
    TvSideSheetRows {
        TvSideSheetRow(label = stringResource(R.string.tv_sheet_close), onClick = nav::pop, modifier = nav.row(KEY_CLOSE))
    }
}

/**
 * The server at a glance, then the groups, then Disconnect after them all: the one destructive thing here goes where a
 * stray OK is least likely to reach it, and asks in a dialog before it runs.
 */
@Composable
private fun ColumnScope.RootPage(
    nav: TvSideSheetNav,
    summary: TvSettingsSummary,
    groups: List<TvSettingsGroup>,
    onDisconnect: () -> Unit,
) {
    var confirmingDisconnect by rememberSaveable { mutableStateOf(false) }
    if (confirmingDisconnect) {
        TvConfirmDialog(
            title = stringResource(R.string.hub_disconnect_confirm_title),
            message = stringResource(R.string.hub_disconnect_confirm_message),
            confirmLabel = stringResource(R.string.tv_settings_disconnect_confirm),
            onConfirm = onDisconnect,
            onDismissRequest = { confirmingDisconnect = false },
            destructive = true,
        )
    }
    TvSideSheetTitle(stringResource(R.string.hub_section_settings))
    TvSideSheetRows {
        TvInfoCard(title = summary.title, lines = summary.lines, icon = Icons.Filled.Dns, modifier = nav.row(KEY_SUMMARY))
        groups.forEach { group ->
            TvSideSheetRow(
                label = group.page.title,
                icon = group.icon,
                onClick = { nav.push(groupPage(group.page.title), openedBy = group.page.title) },
                modifier = nav.row(group.page.title),
                opensPage = true,
            )
        }
        TvSideSheetRow(
            label = stringResource(R.string.hub_disconnect),
            icon = Icons.Filled.LinkOff,
            destructive = true,
            onClick = { confirmingDisconnect = true },
            modifier = nav.row(KEY_DISCONNECT),
        )
    }
}

/** A group's rows to press, then what it reads out as one card; Back is the way up, so there is no Back row. */
@Composable
private fun ColumnScope.GroupPage(
    nav: TvSideSheetNav,
    group: TvPaneGroup,
    household: TvHousehold?,
) {
    TvSideSheetTitle(group.title)
    group.note?.let { TvSideSheetBody(it) }
    val (facts, rows) = group.rows.partition { it.options.isEmpty() }
    TvSideSheetRows {
        rows.forEach { SettingsRow(it, nav) }
        facts.firstOrNull()?.let { head ->
            TvInfoCard(
                title = head.label,
                lines = facts.drop(1).map { it.factLine(stringResource(R.string.hub_meta_separator)) },
                icon = head.icon,
                modifier = nav.row(head.key),
            )
        }
    }
    // Pinned under the rows, not among them, so a long page scrolls under it rather than carrying it out of sight.
    if (household != null && group.isHousehold()) {
        // A refusal has no timer on a television, so it goes when its page does.
        val leave by rememberUpdatedState(household.onLeave)
        DisposableEffect(Unit) { onDispose { leave() } }
        TvSaveStatus(
            status = household.status,
            onRetry = household.onRetry,
            modifier = Modifier.padding(bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
        )
    }
}

private fun TvPaneRow.factLine(separator: String): String = if (body.isEmpty()) label else "$label$separator$body"

/**
 * A pane row as a sheet row that runs its one option. A [switchRow] ends in a switch, which says on or off, with its
 * description under the label. Any other says what it holds in place of its description: a job's or a scan's outcome,
 * or what pressing it does. The media server row is named for what pressing it does, since its own label is the server's.
 */
@Composable
private fun SettingsRow(
    row: TvPaneRow,
    nav: TvSideSheetNav,
) {
    val option = row.options.first()
    val switchedOn = row.switchedOn()
    if (switchedOn != null) {
        TvSideSheetRow(
            label = row.label,
            icon = row.icon,
            onClick = option.onSelect,
            supportingText = row.body.ifEmpty { null },
            modifier = nav.row(row.key),
            role = Role.Switch,
        ) { isFocused -> TvSwitch(checked = switchedOn, isFocused = isFocused) }
        return
    }
    val announce = if (row.announceNote) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier
    val forMediaServer = row.key == KEY_MEDIA_SERVER
    TvSideSheetRow(
        label = if (forMediaServer) option.label else row.label,
        icon = row.icon,
        onClick = option.onSelect,
        supportingText = row.note ?: row.detail ?: if (forMediaServer) row.label else row.body.ifEmpty { null },
        modifier = nav.row(row.key).then(announce),
        opensPage = row.opensPage(),
    )
}

/**
 * Whether pressing the row opens another page of this sheet: the bug report's code, or an agent's page. The pane model is the
 * design system's and does not say, so the row's key does; every other row runs its action or opens another app.
 */
private fun TvPaneRow.opensPage(): Boolean = key == KEY_REPORT_BUG || key.startsWith(KEY_AGENT_PREFIX)

@Composable
private fun ColumnScope.BugReportPage(
    nav: TvSideSheetNav,
    app: AppSettings,
) {
    TvBugReportSheetContent(app = app, codeModifier = nav.row(KEY_CODE))
}
