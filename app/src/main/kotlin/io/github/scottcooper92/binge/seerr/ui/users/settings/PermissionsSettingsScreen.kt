package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.ui.users.PermissionsEditorContent
import kotlinx.coroutines.flow.Flow

/**
 * A permissions editor page: the browser's editor over a [PermissionSettings], saved from its own
 * footer. [titleRes] and [leadRes] are the only thing that differs between the per-user page and
 * the server's default-permissions page, so both are entries into this one.
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
        showSaveAction = false,
        scrolling = false,
    ) { draft, enabled ->
        PermissionsEditorContent(
            offered = draft.offered,
            selected = draft.selected,
            title = stringResource(leadRes),
            saving = !enabled,
            locked = draft.locked,
            onToggle = onToggle,
            onSave = actions.onSave,
        )
    }
}
