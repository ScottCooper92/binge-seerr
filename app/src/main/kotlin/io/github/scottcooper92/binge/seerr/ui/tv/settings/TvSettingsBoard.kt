package io.github.scottcooper92.binge.seerr.ui.tv.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.ConnectionSummary
import io.github.scottcooper92.binge.seerr.ui.settings.ServerSummary
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.SignInKind
import io.github.scottcooper92.binge.seerr.ui.settings.generalRows
import io.github.scottcooper92.binge.seerr.ui.settings.mediaServerRows
import io.github.scottcooper92.binge.seerr.ui.settings.requestPolicyRows
import io.github.scottcooper92.binge.seerr.ui.settings.serviceRows
import io.github.scottcooper92.binge.seerr.ui.settings.systemRows
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheet
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetConfirm
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardFrame
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardPlate
import io.github.scottcooper92.binge.seerr.ui.tv.TvListPaneBoard
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneGroup
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneOption
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneRow
import io.github.scottcooper92.binge.seerr.ui.tv.rememberTvTransientEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal const val KEY_SERVER = "server"
internal const val KEY_SIGNED_IN = "signed-in"
internal const val KEY_VERSION = "version"
internal const val KEY_EDIT_CONNECTION = "edit-connection"
internal const val KEY_DISCONNECT = "disconnect"
internal const val KEY_MEDIA_SERVER = "media-server"

/**
 * Settings as the list/pane board: the connection and the two things a television can do about it — edit
 * it and disconnect — then the admin's read of the server, each row a read-out with a note saying where it
 * is changed. The editing pages stay on the phone; the one exception is the media server row's library
 * scan, a confirmed action rather than a form. Last comes this app's own group, whose bug report is a
 * code for the phone to scan.
 */
@Composable
internal fun TvSettingsBoard(
    state: SettingsUiState,
    onEditConnection: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    onStartLibraryScan: () -> Unit = {},
    libraryScanEvents: Flow<EditorEvent> = emptyFlow(),
    initialFocusedKey: String? = null,
    initialListHasFocus: Boolean = false,
    initialFocusedOptionLabel: String? = null,
    initialShowingBugReport: Boolean = false,
    onToggleShareUsageData: (Boolean) -> Unit = {},
    onToggleSendCrashReports: (Boolean) -> Unit = {},
) {
    val ready = state as? SettingsUiState.Ready
    if (ready == null) {
        TvBoardFrame(title = stringResource(R.string.hub_section_settings), modifier = modifier) {
            TvBoardPlate(body = stringResource(R.string.tv_loading), modifier = Modifier.weight(1f))
        }
        return
    }
    var confirmingDisconnect by rememberSaveable { mutableStateOf(false) }
    var showingBugReport by rememberSaveable { mutableStateOf(initialShowingBugReport) }
    // Saved so a return to Settings resumes where the user was rather than snapping back to the top.
    var focusedKey by rememberSaveable { mutableStateOf(initialFocusedKey) }
    // The Disconnect option that opened the confirm sheet, so the closer has somewhere to hand focus back to.
    val disconnectFocus = remember { FocusRequester() }
    val closer = rememberTvOverlayCloser(restoreTo = disconnectFocus, onClose = { confirmingDisconnect = false })
    val bugReportFocus = remember { FocusRequester() }
    val bugReportCloser = rememberTvOverlayCloser(restoreTo = bugReportFocus, onClose = { showingBugReport = false })
    // A newer notice supersedes the one still showing, same as the board's paged-list toasts.
    val scanNote = rememberTvTransientEvent(libraryScanEvents).tvNoteOrNull()
    val groups =
        tvSettingGroups(
            ready,
            onEditConnection = onEditConnection,
            onDisconnect = { confirmingDisconnect = true },
            disconnectFocus = disconnectFocus,
            onStartLibraryScan = onStartLibraryScan,
            scanNote = scanNote,
        ) +
            listOfNotNull(
                ready.app?.let { app ->
                    tvAppGroup(
                        app = app,
                        actions = TvAppActions({ showingBugReport = true }, onToggleShareUsageData, onToggleSendCrashReports),
                        optionFocus = bugReportFocus,
                    )
                },
            )
    val describedKey = focusedKey?.takeIf { key -> groups.any { group -> group.rows.any { it.key == key } } } ?: KEY_SERVER
    Box(modifier = modifier.fillMaxSize()) {
        TvListPaneBoard(
            title = stringResource(R.string.hub_section_settings),
            groups = groups,
            focusedKey = describedKey,
            onFocusRow = { focusedKey = it },
            initialListHasFocus = initialListHasFocus,
            initialFocusedOptionLabel = initialFocusedOptionLabel,
        )
        val app = ready.app
        if (showingBugReport && app != null) {
            TvActionSheet(onDismiss = bugReportCloser::close) { entryFocus ->
                TvBugReportSheetContent(app = app, onClose = bugReportCloser::close, entryFocus = entryFocus)
            }
        }
        if (confirmingDisconnect) {
            TvActionSheet(onDismiss = closer::close) { entryFocus ->
                TvActionSheetConfirm(
                    title = stringResource(R.string.hub_disconnect_confirm_title),
                    message = stringResource(R.string.hub_disconnect_confirm_message),
                    confirmLabel = stringResource(R.string.tv_settings_disconnect_confirm),
                    onConfirm = {
                        onDisconnect()
                        closer.close()
                    },
                    onCancel = closer::close,
                    entryFocus = entryFocus,
                )
            }
        }
    }
}

@Composable
private fun tvSettingGroups(
    state: SettingsUiState.Ready,
    onEditConnection: () -> Unit,
    onDisconnect: () -> Unit,
    disconnectFocus: FocusRequester,
    onStartLibraryScan: () -> Unit,
    scanNote: String?,
): List<TvPaneGroup> {
    val config = state.config
    val readOnly = stringResource(R.string.tv_settings_read_only_note)
    return buildList {
        add(
            TvPaneGroup(
                stringResource(R.string.settings_group_connection),
                connectionRows(state.connection, state.server, onEditConnection, onDisconnect, disconnectFocus),
            ),
        )
        config?.general?.let {
            add(readOnlyGroup(stringResource(R.string.settings_group_general), generalRows(it) {}, readOnly))
        }
        // Admin-only, same as the phone: this is the one section that also gets an action, so it is
        // built by hand rather than through the read-only mapping every other section shares.
        if (config != null) {
            add(mediaServerGroup(state.server, onStartLibraryScan, note = scanNote ?: readOnly))
        }
        config?.services?.let {
            add(readOnlyGroup(stringResource(R.string.settings_group_services), serviceRows(it, {}) { _, _ -> }, readOnly))
        }
        config?.requestPolicy?.let { add(readOnlyGroup(stringResource(R.string.settings_group_requests), requestPolicyRows(it), readOnly)) }
        config?.system?.let {
            add(readOnlyGroup(stringResource(R.string.settings_group_system), systemRows(it) {}, readOnly))
        }
    }.filter { it.rows.isNotEmpty() }
}

/**
 * The media server row, with one option beside its read-out: start the library scan job the phone's
 * Jobs page runs, the "it downloaded but isn't showing" fix from the sofa. [note] is the scan's own
 * outcome while it is still showing, else the same "change this on the phone" note every other row here
 * carries.
 */
@Composable
private fun mediaServerGroup(
    server: ServerSummary,
    onStartLibraryScan: () -> Unit,
    note: String,
): TvPaneGroup {
    val row = mediaServerRows(server) {}.first()
    return TvPaneGroup(
        title = stringResource(R.string.server_settings_media_server),
        rows =
            listOf(
                TvPaneRow(
                    key = KEY_MEDIA_SERVER,
                    label = row.label,
                    body = row.detail.orEmpty(),
                    note = note,
                    options =
                        listOf(
                            TvPaneOption(label = stringResource(R.string.tv_settings_start_library_scan), onSelect = onStartLibraryScan),
                        ),
                    icon = row.icon,
                ),
            ),
    )
}

/** The line to show for a job outcome; null for an event this row never emits (a save or a delete). */
@Composable
private fun EditorEvent?.tvNoteOrNull(): String? =
    when (this) {
        null -> null
        is EditorEvent.Notice -> stringResource(messageRes)
        is EditorEvent.Failed -> stringResource(error.messageRes())
        EditorEvent.Saved, EditorEvent.Deleted -> null
    }

/** The phone's rows as read-outs: the label and its detail, and where to change it. */
private fun readOnlyGroup(
    title: String,
    rows: List<SettingsRow>,
    note: String,
): TvPaneGroup =
    TvPaneGroup(
        title = title,
        rows =
            rows.map { row ->
                TvPaneRow(key = "$title/${row.label}", label = row.label, body = row.detail.orEmpty(), note = note, icon = row.icon)
            },
    )

@Composable
private fun connectionRows(
    connection: ConnectionSummary,
    server: ServerSummary,
    onEditConnection: () -> Unit,
    onDisconnect: () -> Unit,
    disconnectFocus: FocusRequester,
): List<TvPaneRow> =
    listOf(
        TvPaneRow(key = KEY_SERVER, label = stringResource(R.string.settings_server), body = connection.baseUrl, icon = Icons.Filled.Link),
        TvPaneRow(
            key = KEY_SIGNED_IN,
            label = stringResource(R.string.settings_signed_in_as),
            body =
                when (connection.signInKind) {
                    SignInKind.ApiKey -> stringResource(R.string.settings_signed_in_api_key)
                    SignInKind.Session -> connection.userName ?: stringResource(R.string.settings_value_unknown)
                },
            icon = Icons.Filled.Person,
        ),
        TvPaneRow(
            key = KEY_VERSION,
            label = stringResource(R.string.settings_version),
            body = server.versionLine(),
            icon = Icons.Filled.Dns,
        ),
        TvPaneRow(
            key = KEY_EDIT_CONNECTION,
            label = stringResource(R.string.settings_edit_connection),
            body = stringResource(R.string.settings_edit_connection_caption),
            options = listOf(TvPaneOption(label = stringResource(R.string.settings_edit_connection), onSelect = onEditConnection)),
            icon = Icons.Filled.Edit,
        ),
        TvPaneRow(
            key = KEY_DISCONNECT,
            label = stringResource(R.string.hub_disconnect),
            body = stringResource(R.string.hub_disconnect_confirm_message),
            options =
                listOf(
                    TvPaneOption(
                        label = stringResource(R.string.hub_disconnect),
                        onSelect = onDisconnect,
                        focusRequester = disconnectFocus,
                    ),
                ),
            icon = Icons.Filled.PowerSettingsNew,
        ),
    )

@Composable
private fun ServerSummary.versionLine(): String {
    val edition =
        versionLabel?.let { stringResource(R.string.setup_server_edition, variant.displayName, it) }
            ?: stringResource(R.string.setup_server_development, variant.displayName)
    return when {
        commitsBehind > 0 -> stringResource(R.string.settings_version_behind, edition, commitsBehind)
        updateAvailable -> stringResource(R.string.settings_version_update, edition)
        else -> edition
    }
}
