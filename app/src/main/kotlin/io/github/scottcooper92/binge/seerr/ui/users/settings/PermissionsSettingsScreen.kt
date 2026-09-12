package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.ui.users.PermissionsEditorContent
import kotlinx.coroutines.flow.Flow

/** The permissions page: the browser's editor over one user, saved from its own footer. */
@Composable
fun PermissionsSettingsScreen(
    state: EditorUiState<PermissionSettings>,
    events: Flow<EditorEvent>,
    actions: EditorActions<PermissionSettings>,
    onToggle: (ManageablePermission) -> Unit,
) {
    EditorPage(
        title = stringResource(R.string.user_permissions_title),
        state = state,
        events = events,
        actions = actions,
        showSaveAction = false,
        scrolling = false,
    ) { draft, enabled ->
        PermissionsEditorContent(
            offered = draft.offered,
            selected = draft.selected,
            title = stringResource(R.string.user_settings_permissions_lead),
            saving = !enabled,
            locked = draft.locked,
            onToggle = onToggle,
            onSave = actions.onSave,
        )
    }
}
