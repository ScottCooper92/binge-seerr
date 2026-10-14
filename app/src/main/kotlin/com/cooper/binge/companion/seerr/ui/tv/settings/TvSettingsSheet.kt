package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.binge.designsystem.tv.component.TvConfirmDialog
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import com.binge.designsystem.tv.R as TvR
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import com.binge.designsystem.tv.component.TvSideSheetReadout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetConfirm
import com.binge.designsystem.tv.component.TvSideSheetNav
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetStack
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.binge.designsystem.R as DesR

internal const val PAGE_SETTINGS = "settings"
internal const val PAGE_DISCONNECT = "disconnect"
internal const val PAGE_BUG_REPORT = "bug-report"
private const val PAGE_GROUP_PREFIX = "group:"
private const val PAGE_JOB_PREFIX = "job-page:"
private const val KEY_ACTION = "action"
private const val KEY_BACK = "back"
private const val KEY_CANCEL = "cancel"
private const val KEY_CLOSE = "close"

/** The page a job opens, named by its id. */
internal fun jobPage(id: String): String = PAGE_JOB_PREFIX + id

/** The page a group opens, named by its title: a group appears or goes as the hub reads the server, so an index would shift. */
internal fun groupPage(title: String): String = PAGE_GROUP_PREFIX + title

/**
 * Settings as a stack of side-sheet pages, like Jellyfin's TV preferences: the first lists the groups, each opens its own page
 * of rows, and Disconnect comes last and asks first. Back goes up a page, and from the first closes the sheet through
 * [onDismissRequest]. Rows that read the server are out of the focus order, with a note saying the phone changes them;
 * the few that do something (edit the connection, scan the library, report a bug, the two switches) are rows to press.
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
    TvSideSheetStack(root = initialPage, onDismissRequest = onDismissRequest, modifier = modifier, toBottomEdge = true) { nav ->
        when {
            ready == null -> LoadingPage(nav)
            nav.page.startsWith(PAGE_JOB_PREFIX) ->
                JobPage(
                    nav,
                    TvJobs(jobs, actions.jobs, onOpenJob = {
                    }),
                    nav.page.removePrefix(PAGE_JOB_PREFIX),
                    ready.now,
                )
            nav.page == PAGE_DISCONNECT -> DisconnectPage(nav, onDisconnect = actions.onDisconnect)
            nav.page == PAGE_BUG_REPORT && ready.app != null -> BugReportPage(nav, ready.app)
            else -> {
                val tvJobs =
                    TvJobs(jobs, actions.jobs, onOpenJob = { id -> nav.push(jobPage(id), openedBy = "$KEY_JOB_PREFIX$id") })
                val groups =
                    tvSettingGroups(ready, actions, tvJobs, hub, onShowBugReport = { nav.push(PAGE_BUG_REPORT, KEY_REPORT_BUG) })
                val group = groups.firstOrNull { groupPage(it.title) == nav.page }
                if (group != null) GroupPage(nav, group) else RootPage(nav, groups, tvSettingsSummary(ready, hub), actions.onDisconnect)
            }
        }
    }
}

@Composable
private fun ColumnScope.LoadingPage(nav: TvSideSheetNav) {
    TvSideSheetTitle(stringResource(R.string.hub_section_settings))
    TvSideSheetBody(stringResource(R.string.tv_loading))
    TvSideSheetRow(label = stringResource(R.string.tv_sheet_close), onClick = nav::pop, modifier = nav.row(KEY_CLOSE))
}

/** PROTOTYPE: the server at a glance in one card, the groups, then Sign out and Disconnect last. */
@Composable
private fun ColumnScope.RootPage(
    nav: TvSideSheetNav,
    groups: List<TvPaneGroup>,
    summary: List<TvPaneRow>,
    onDisconnect: () -> Unit,
) {
    // PROTOTYPE: the confirm is a centred dialog over the sheet rather than a page in it.
    var confirmingDisconnect by remember { mutableStateOf(false) }
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
    ScrollingRows {
        if (summary.isNotEmpty()) TvSettingsSummaryCard(summary, modifier = nav.row(KEY_SUMMARY))
        groups.forEach { group ->
            TvSideSheetRow(
                label = group.title,
                icon =
                    when (group.title) {
                        stringResource(R.string.tv_settings_group_maintenance_proto) -> Icons.Filled.Build
                        stringResource(R.string.settings_group_app) -> Icons.Filled.PhoneAndroid
                        else -> group.rows.firstOrNull()?.icon
                    },
                onClick = { nav.push(groupPage(group.title), openedBy = group.title) },
                modifier = nav.row(group.title),
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

private const val KEY_SUMMARY = "summary"

/**
 * PROTOTYPE: facts as one tile, an [icon] at its top left and a [title] over its lines. It takes focus, so the remote can
 * scroll it into view, and shows the primary ring a focused control does, but never a fill: there is nothing to press.
 */
@Composable
internal fun TvInfoCard(
    icon: ImageVector,
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    TvSettingsSummaryCard(
        listOf(TvPaneRow(key = title, label = title, body = lines.firstOrNull().orEmpty())) +
            lines.drop(1).map { TvPaneRow(key = it, label = "", body = it) },
        modifier = modifier,
        icon = icon,
    )
}

/** PROTOTYPE: a switch's state at the end of its row: a track with a thumb at the end it is set to. */
@Composable
private fun TvSwitchIndicator(
    on: Boolean,
    focused: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val track = dimensionResource(TvR.dimen.tv_button_icon)
    val thumbColor = if (focused) scheme.onPrimary else if (on) scheme.onPrimary else scheme.onSurfaceVariant
    val trackColor =
        when {
            on && focused -> scheme.onSurface.copy(alpha = 0.35f)
            on -> scheme.primary
            else -> scheme.surfaceVariant
        }
    Box(
        modifier =
            Modifier
                .size(width = track * 2, height = track)
                .clip(RoundedCornerShape(track))
                .background(trackColor)
                .padding(dimensionResource(DesR.dimen.padding_xs) / 2),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.size(track - dimensionResource(DesR.dimen.padding_xs)).clip(RoundedCornerShape(track)).background(thumbColor))
    }
}

/**
 * PROTOTYPE: the summary as one tile: an icon at its top left and the facts as a column of text beside it. It takes focus,
 * so the remote can scroll it into view, but shows only a quiet ring, never the accent fill a row to press gets.
 */
@Composable
private fun TvSettingsSummaryCard(
    summary: List<TvPaneRow>,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Dns,
    /** Whether the first fact is the card's title; otherwise every fact is one line of label then value. */
    titled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(dimensionResource(DesR.dimen.padding_sm))
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .semantics(mergeDescendants = true) {}
                .clip(shape)
                .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                .border(dimensionResource(TvR.dimen.tv_button_border_width), if (focused) scheme.primary else Color.Transparent, shape)
                .padding(dimensionResource(DesR.dimen.padding_m)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(TvR.dimen.tv_button_icon)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs))) {
            if (titled) {
                val first = summary.first()
                Text(text = first.label, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                val lines = listOfNotNull(first.body.ifEmpty { null }) + summary.drop(1).map { listOf(it.body, it.label).filter(String::isNotEmpty).joinToString(" ") }
                lines.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
            } else {
                summary.forEach { fact ->
                    Column {
                        Text(text = fact.label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                        if (fact.body.isNotEmpty()) Text(text = fact.body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.GroupPage(
    nav: TvSideSheetNav,
    group: TvPaneGroup,
) {
    TvSideSheetTitle(group.title)
    group.note?.let { TvSideSheetBody(it) }
    ScrollingRows {
        val (facts, actions) = group.rows.partition { it.options.isEmpty() }
        actions.forEach { SettingsRow(it, nav) }
        if (facts.isNotEmpty()) {
            TvSettingsSummaryCard(
                facts.map { it.copy(label = it.label, body = it.body) },
                modifier = nav.row("${group.title}/facts"),
                icon = facts.first().icon ?: Icons.Filled.Dns,
                titled = false,
            )
        }
    }
}

/** A page's rows in a column that scrolls with focus, since a group can hold more than the panel is tall. */
@Composable
private fun ColumnScope.ScrollingRows(content: @Composable ColumnScope.() -> Unit) {
    Column(
        // To the bottom of the screen, with the overscan inset inside the scroll so the last row clears the edge.
        modifier =
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
        content = content,
    )
}

/**
 * A pane row as a sheet row. One with no option is a read-out, out of the focus order. One with an option is a row to
 * press that runs it, saying what it holds in place of its description (a switch's state, a scan's outcome). The media
 * server row is named for what pressing it does, since its own label is the server's.
 */
@Composable
private fun SettingsRow(
    row: TvPaneRow,
    nav: TvSideSheetNav,
) {
    val option = row.options.firstOrNull()
    if (option == null) {
        TvSideSheetReadout(label = row.label, icon = row.icon, supportingText = row.body.ifEmpty { null })
        return
    }
    // PROTOTYPE: a switch row says where it stands with a switch at its end, not a line of text.
    val onText = stringResource(R.string.tv_settings_toggle_on)
    val offText = stringResource(R.string.tv_settings_toggle_off)
    val switchedOn = when (row.note) {
        onText -> true
        offText -> false
        else -> null
    }
    val announce = if (row.announceNote) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier
    val forMediaServer = row.key == KEY_MEDIA_SERVER
    TvSideSheetRow(
        label = if (forMediaServer) option.label else row.label,
        icon = row.icon,
        onClick = option.onSelect,
        supportingText = (if (switchedOn != null) null else row.note) ?: row.detail ?: if (forMediaServer) row.label else row.body.ifEmpty { null },
        modifier = nav.row(row.key).then(announce),
        trailing = switchedOn?.let { on -> { focused -> TvSwitchIndicator(on = on, focused = focused) } },
    )
}

/**
 * One job: what it does and where it stands, with Run now, or Cancel while it runs, on a row of its own. A call on its way, or
 * one that was refused, is said here in place until the job's next attempt, as a live region.
 */
@Composable
private fun ColumnScope.JobPage(
    nav: TvSideSheetNav,
    jobs: TvJobs,
    id: String,
    now: Long,
) {
    val job = jobs.state.jobOrNull(id)
    val status = jobs.state.statusOf(id)
    val running = job?.running == true
    TvSideSheetTitle(job?.name ?: stringResource(R.string.settings_group_system))
    TvSideSheetBody(stringResource(R.string.tv_settings_job_body))
    jobLine(job, status, now)?.let { TvSideSheetBody(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    if (job != null) {
        TvSideSheetRow(
            label = stringResource(if (running) R.string.server_settings_job_cancel else R.string.server_settings_job_run),
            icon = if (running) Icons.Filled.Cancel else Icons.Filled.PlayArrow,
            enabled = status != TvJobStatus.Busy,
            onClick = { if (running) jobs.actions.onCancel(id) else jobs.actions.onRun(id) },
            modifier = nav.row(KEY_ACTION),
        )
    }
    TvSideSheetRow(label = stringResource(DesR.string.cd_navigate_back), onClick = nav::pop, modifier = nav.row(KEY_BACK))
}

/** Disconnect's confirm, as a page of its own: Cancel takes focus and pops back to the Disconnect row. */
@Composable
private fun ColumnScope.DisconnectPage(
    nav: TvSideSheetNav,
    onDisconnect: () -> Unit,
) {
    TvSideSheetConfirm(
        title = stringResource(R.string.hub_disconnect_confirm_title),
        message = stringResource(R.string.hub_disconnect_confirm_message),
        confirmLabel = stringResource(R.string.tv_settings_disconnect_confirm),
        onConfirm = onDisconnect,
        onCancel = nav::pop,
        entryFocus = null,
        cancelModifier = nav.row(KEY_CANCEL),
    )
    nav.arriveAt(KEY_CANCEL)
}

@Composable
private fun ColumnScope.BugReportPage(
    nav: TvSideSheetNav,
    app: AppSettings,
) {
    TvBugReportSheetContent(app = app, onClose = nav::pop, closeModifier = nav.row(KEY_CLOSE))
}
