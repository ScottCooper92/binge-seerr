package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.ui.users.PermissionsEditorContent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.PermissionSettings
import kotlinx.coroutines.flow.Flow

/** The default permissions page: the users' editor over what a new account starts with, saved from its own footer. */
@Composable
fun DefaultPermissionsScreen(
    state: EditorUiState<PermissionSettings>,
    events: Flow<EditorEvent>,
    actions: EditorActions<PermissionSettings>,
    onToggle: (ManageablePermission) -> Unit,
) {
    EditorPage(
        title = stringResource(R.string.server_settings_default_permissions),
        state = state,
        events = events,
        actions = actions,
        showSaveAction = false,
        scrolling = false,
    ) { draft, enabled ->
        PermissionsEditorContent(
            offered = draft.offered,
            selected = draft.selected,
            title = stringResource(R.string.server_settings_default_permissions_lead),
            saving = !enabled,
            locked = draft.locked,
            onToggle = onToggle,
            onSave = actions.onSave,
        )
    }
}
