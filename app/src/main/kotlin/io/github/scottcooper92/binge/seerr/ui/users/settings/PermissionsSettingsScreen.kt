package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.PermissionGroup
import io.github.scottcooper92.binge.seerr.ui.users.labelRes
import io.github.scottcooper92.binge.seerr.ui.users.permissionToggles
import kotlinx.coroutines.flow.Flow

internal const val PERMISSIONS_FORM_KEY = "permissions"

/**
 * A permissions editor page, as one collapsible section per permission group (#549), saved from the
 * pinned Cancel and Save bar. Nothing here can be wrong, so the validation has no issues: it is what
 * opts the page into the sectioned form. [titleRes] and [leadRes] are the only thing that differs
 * between the per-user page and the server's default-permissions page, so both are entries into
 * this one.
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
    val validation = remember { EditorValidation<PermissionSettings>(PERMISSIONS_FORM_KEY) { emptyList() } }
    EditorPage(
        title = stringResource(titleRes),
        state = state,
        events = events,
        actions = actions,
        validation = validation,
    ) { draft, enabled ->
        Text(stringResource(leadRes), style = MaterialTheme.typography.titleMedium)
        draft.offered.groupBy { it.group }.forEach { (group, permissions) ->
            EditorSection(
                sectionId = group.name,
                title = stringResource(group.labelRes()),
                defaultExpanded = permissionGroupStartsOpen(group, draft.selected),
            ) {
                EditorToggleRows(
                    permissionToggles(permissions, draft.selected, saving = !enabled, locked = draft.locked, onToggle = onToggle),
                )
            }
        }
    }
}

/**
 * Administration starts closed unless it already grants something: it is the rare and risky choice,
 * and the user's everyday decisions are in the groups below it. Every other group starts open.
 */
internal fun permissionGroupStartsOpen(
    group: PermissionGroup,
    selected: Set<ManageablePermission>,
): Boolean = group != PermissionGroup.Administration || selected.any { it.group == PermissionGroup.Administration }
