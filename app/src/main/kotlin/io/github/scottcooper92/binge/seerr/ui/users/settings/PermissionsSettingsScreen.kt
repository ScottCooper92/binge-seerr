package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.ui.users.labelRes
import io.github.scottcooper92.binge.seerr.ui.users.permissionRows
import io.github.scottcooper92.binge.seerr.ui.users.permissionTree
import kotlinx.coroutines.flow.Flow

/**
 * A permissions editor page, laid out as the web client's permission tree: one group of switch rows per permission
 * group, each parent with the permissions it covers joined beneath it. [titleRes] and [leadRes] are the only thing
 * that differs between the per-user page and the server's default-permissions page, so both are entries into this one.
 */
@Composable
fun PermissionsSettingsScreen(
    state: EditorUiState<PermissionSettings>,
    events: Flow<EditorEvent>,
    actions: EditorActions<PermissionSettings>,
    onToggle: (ManageablePermission) -> Unit,
    @StringRes titleRes: Int = R.string.user_permissions_title,
    @StringRes leadRes: Int = R.string.user_settings_permissions_lead,
) {
    EditorPage(
        title = stringResource(titleRes),
        state = state,
        events = events,
        actions = actions,
        saveAsMade = true,
    ) { draft, enabled ->
        GroupMessage(stringResource(leadRes), error = false)
        permissionTree(draft.offered, draft.selected).forEach { (group, nodes) ->
            ItemGroup(
                title = stringResource(group.labelRes()),
                rows = permissionRows(nodes, draft.selected, saving = !enabled, locked = draft.locked, onToggle = onToggle),
            )
        }
    }
}
