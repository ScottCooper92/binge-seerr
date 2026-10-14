package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
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
import com.cooper.binge.companion.seerr.ui.tv.rememberTvTransientEvent
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.binge.designsystem.R as DesR

internal const val PAGE_SETTINGS = "settings"
internal const val PAGE_DISCONNECT = "disconnect"
internal const val PAGE_BUG_REPORT = "bug-report"
private const val PAGE_GROUP_PREFIX = "group:"
private const val KEY_BACK = "back"
private const val KEY_CANCEL = "cancel"
private const val KEY_CLOSE = "close"

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
    libraryScanEvents: Flow<EditorEvent> = emptyFlow(),
    hub: HubUiState? = null,
    initialPage: String = PAGE_SETTINGS,
    /** The scan note showing on arrival, for a frame, which never collects [libraryScanEvents]. Production passes null. */
    initialScanEvent: EditorEvent? = null,
) {
    val ready = state as? SettingsUiState.Ready
    // A newer notice supersedes the one still showing, same as the board's paged-list toasts.
    val scanNote = rememberTvTransientEvent(libraryScanEvents, initialScanEvent).tvNoteOrNull()
    TvSideSheetStack(root = initialPage, onDismissRequest = onDismissRequest, modifier = modifier) { nav ->
        when {
            ready == null -> LoadingPage(nav)
            nav.page == PAGE_DISCONNECT -> DisconnectPage(nav, onDisconnect = actions.onDisconnect)
            nav.page == PAGE_BUG_REPORT && ready.app != null -> BugReportPage(nav, ready.app)
            else -> {
                val groups =
                    tvSettingGroups(ready, actions, scanNote, hub, onShowBugReport = { nav.push(PAGE_BUG_REPORT, KEY_REPORT_BUG) })
                val group = groups.firstOrNull { groupPage(it.title) == nav.page }
                if (group != null) GroupPage(nav, group) else RootPage(nav, groups)
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

/** The groups, then Disconnect after them all: the one destructive thing here goes where a stray OK is least likely to reach it. */
@Composable
private fun ColumnScope.RootPage(
    nav: TvSideSheetNav,
    groups: List<TvPaneGroup>,
) {
    TvSideSheetTitle(stringResource(R.string.hub_section_settings))
    ScrollingRows {
        groups.forEach { group ->
            TvSideSheetRow(
                label = group.title,
                icon = group.rows.firstOrNull()?.icon,
                onClick = { nav.push(groupPage(group.title), openedBy = group.title) },
                modifier = nav.row(group.title),
            )
        }
        TvSideSheetRow(
            label = stringResource(R.string.hub_disconnect),
            icon = Icons.Filled.LinkOff,
            destructive = true,
            onClick = { nav.push(PAGE_DISCONNECT, openedBy = KEY_DISCONNECT) },
            modifier = nav.row(KEY_DISCONNECT),
        )
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
        group.rows.forEach { SettingsRow(it, nav) }
        TvSideSheetRow(label = stringResource(DesR.string.cd_navigate_back), onClick = nav::pop, modifier = nav.row(KEY_BACK))
    }
}

/** A page's rows in a column that scrolls with focus, since a group can hold more than the panel is tall. */
@Composable
private fun ColumnScope.ScrollingRows(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
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
        TvSideSheetRow(label = row.label, icon = row.icon, enabled = false, onClick = {}, supportingText = row.body.ifEmpty { null })
        return
    }
    val announce = if (row.announceNote) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier
    val forMediaServer = row.key == KEY_MEDIA_SERVER
    TvSideSheetRow(
        label = if (forMediaServer) option.label else row.label,
        icon = row.icon,
        onClick = option.onSelect,
        supportingText = row.note ?: if (forMediaServer) row.label else row.body.ifEmpty { null },
        modifier = nav.row(row.key).then(announce),
    )
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
