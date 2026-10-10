package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeBottomSheet
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleGroup
import com.binge.designsystem.R as DesR

/**
 * The permission toggles, grouped, with the umbrella rules: a permission another selected one
 * already covers reads on and locked. A save writes only what was toggled, onto each user, so the sheet
 * says how many users it lands on, and notes when they don't all share the same permissions (#1007).
 */
@Composable
internal fun PermissionsEditorSheet(
    offered: List<ManageablePermission>,
    edit: BulkEdit,
    /** What this viewer may not grant: shown, and not flippable, as on one user's own Permissions page (#1008). */
    locked: Set<ManageablePermission>,
    userCount: Int,
    onToggle: (ManageablePermission) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss, gesturesEnabled = !edit.saving) {
        PermissionsEditorContent(
            offered = offered,
            selected = edit.selected,
            title = pluralStringResource(R.plurals.users_edit_permissions_title, userCount, userCount),
            saving = edit.saving,
            onToggle = onToggle,
            onSave = onSave,
            locked = locked,
            note = if (edit.mixed.isEmpty()) null else stringResource(R.string.users_edit_permissions_mixed),
            mixed = edit.mixed,
            undecided = edit.undecided,
        )
    }
}

@Composable
internal fun PermissionsEditorContent(
    offered: List<ManageablePermission>,
    selected: Set<ManageablePermission>,
    title: String,
    saving: Boolean,
    onToggle: (ManageablePermission) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** Toggles shown but not flippable: what the viewer may not grant. */
    locked: Set<ManageablePermission> = emptySet(),
    /** A line under the title, such as how a bulk edit treats permissions the selection doesn't share. */
    note: String? = null,
    /** A bulk edit's permissions only some selected users hold, and those of them still left as each user has them. */
    mixed: Set<ManageablePermission> = emptySet(),
    undecided: Set<ManageablePermission> = emptySet(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                        .padding(bottom = dimensionResource(DesR.dimen.padding_s)),
            )
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                            .padding(bottom = dimensionResource(DesR.dimen.padding_m)),
                )
            }
            // A permission someone selected holds keeps its row through the whole cycle, even one the tree hides by default.
            permissionTree(offered, selected + mixed).forEach { (group, nodes) ->
                EditorToggleGroup(
                    stringResource(group.labelRes()),
                    permissionRows(nodes, selected, saving, locked, onToggle, undecided),
                    modifier =
                        Modifier
                            .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                            .padding(bottom = dimensionResource(DesR.dimen.padding_m)),
                )
            }
        }
        BingeActionFooter(
            label = stringResource(R.string.users_edit_permissions_save),
            onClick = onSave,
            enabled = !saving,
            loading = saving,
            modifier =
                Modifier
                    .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                    .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        )
    }
}
