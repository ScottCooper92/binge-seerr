package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.settings.labelRes
import com.cooper.binge.companion.seerr.ui.settings.requestPolicyRows
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent

internal const val KEY_SERVER = "server"
internal const val KEY_SIGNED_IN = "signed-in"
internal const val KEY_VERSION = "version"
internal const val KEY_EDIT_CONNECTION = "edit-connection"
internal const val KEY_DISCONNECT = "disconnect"
internal const val KEY_MEDIA_SERVER = "media-server"

/** What the settings sheet can ask of its host, in one place so the entry stays a wiring. */
internal class TvSettingsActions(
    val onEditConnection: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onStartLibraryScan: () -> Unit = {},
    val onToggleShareUsageData: (Boolean) -> Unit = {},
    val onToggleSendCrashReports: (Boolean) -> Unit = {},
    val onOpenBingeListing: () -> Unit = {},
)

/**
 * The sheet's groups, in order: the connection and what can be done to it, the server's status, the admin's read of the
 * server and, last, this app's own. Disconnect is not among them: the sheet puts it after them all.
 */
@Composable
internal fun tvSettingGroups(
    state: SettingsUiState.Ready,
    actions: TvSettingsActions,
    scanNote: String?,
    hub: HubUiState?,
    onShowBugReport: () -> Unit,
): List<TvPaneGroup> {
    val config = state.config
    val configGroups =
        buildList {
            config?.general?.let { add(readOnlyGroup(stringResource(R.string.settings_group_general), generalRows(it))) }
            // Admin-only, same as the phone: this is the one section that also gets an action, so it is
            // built by hand rather than through the read-only mapping every other section shares.
            if (config != null) add(mediaServerGroup(state.server, actions.onStartLibraryScan, scanNote = scanNote))
            config?.services?.let { add(readOnlyGroup(stringResource(R.string.settings_group_services), serviceRows(it))) }
            config?.requestPolicy?.let { add(readOnlyGroup(stringResource(R.string.settings_group_requests), requestPolicyRows(it))) }
            config?.system?.let { add(readOnlyGroup(stringResource(R.string.settings_group_system), systemRows(it, state.now))) }
        }.filter { it.rows.isNotEmpty() }
    return buildList {
        add(
            TvPaneGroup(
                stringResource(R.string.settings_group_connection),
                connectionRows(state.connection, state.server, actions.onEditConnection),
            ),
        )
        hub?.let { serverStatusGroup(it, actions.onOpenBingeListing) }?.let(::add)
        addAll(configGroups.carryingNote(stringResource(R.string.tv_settings_read_only_note)))
        state.app?.let { app ->
            add(tvAppGroup(app, TvAppActions(onShowBugReport, actions.onToggleShareUsageData, actions.onToggleSendCrashReports)))
        }
    }.filter { it.rows.isNotEmpty() }
}

/** The "change this on the phone" line, on each of the server's groups: each is its own page in the sheet (#1439). */
internal fun List<TvPaneGroup>.carryingNote(note: String): List<TvPaneGroup> = map { it.copy(note = note) }

/**
 * The media server row, with one option beside its read-out: start the library scan job that the phone's Jobs & cache page's
 * job row runs, the "it downloaded but isn't showing" fix from the sofa. [scanNote] is the scan's own outcome while it is
 * still showing, announced as it arrives, else none.
 */
@Composable
private fun mediaServerGroup(
    server: ServerSummary,
    onStartLibraryScan: () -> Unit,
    scanNote: String?,
): TvPaneGroup =
    TvPaneGroup(
        title = stringResource(R.string.server_settings_media_server),
        rows =
            listOf(
                TvPaneRow(
                    key = KEY_MEDIA_SERVER,
                    label = stringResource(server.mediaServer.labelRes()),
                    body = stringResource(R.string.server_settings_media_server_caption),
                    note = scanNote,
                    announceNote = scanNote != null,
                    options =
                        listOf(
                            TvPaneOption(
                                label = stringResource(R.string.tv_settings_start_library_scan),
                                icon = Icons.Filled.Sync,
                                onSelect = onStartLibraryScan,
                            ),
                        ),
                    icon = Icons.Filled.Storage,
                ),
            ),
    )

/** The line to show for a job outcome; null for an event this row never emits (a save or a delete). */
@Composable
internal fun EditorEvent?.tvNoteOrNull(): String? =
    when (this) {
        null -> null
        is EditorEvent.Notice -> stringResource(messageRes)
        is EditorEvent.Failed -> stringResource(error.messageRes())
        EditorEvent.Saved, EditorEvent.Deleted, is EditorEvent.Undoable -> null
    }

/**
 * The phone's rows as read-outs: the label and its detail, and where to change it. Each row is keyed by place, not label:
 * Seerr doesn't make instance names unique, and two rows sharing a key share focus (#865).
 */
internal fun readOnlyGroup(
    title: String,
    rows: List<ListItem>,
): TvPaneGroup =
    TvPaneGroup(
        title = title,
        rows =
            rows.mapIndexed { index, row ->
                TvPaneRow(
                    key = "$title/$index",
                    label = row.label,
                    body = row.detail.orEmpty(),
                    icon = row.icon,
                )
            },
    )

@Composable
private fun connectionRows(
    connection: ConnectionSummary,
    server: ServerSummary,
    onEditConnection: () -> Unit,
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
            options =
                listOf(
                    TvPaneOption(
                        label = stringResource(R.string.settings_edit_connection),
                        icon = Icons.Filled.Edit,
                        onSelect = onEditConnection,
                    ),
                ),
            icon = Icons.Filled.Edit,
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
