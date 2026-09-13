package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeSheetFooter
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import com.binge.designsystem.R as DesR

/**
 * The permission toggles, grouped, with the umbrella rules: a permission another selected one
 * already covers reads on and locked. The set written is the whole new set, so the sheet says how
 * many users it lands on.
 */
@Composable
internal fun PermissionsEditorSheet(
    offered: List<ManageablePermission>,
    edit: BulkEdit,
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
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            offered.groupBy { it.group }.forEach { (group, permissions) ->
                Text(
                    stringResource(group.labelRes()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            horizontal = dimensionResource(DesR.dimen.padding_m),
                            vertical = dimensionResource(DesR.dimen.padding_s),
                        ),
                )
                permissions.forEach { permission ->
                    val implied = permission !in selected && ManageablePermission.isGranted(permission, selected)
                    PermissionRow(
                        label = stringResource(permission.labelRes()),
                        checked = permission in selected || implied,
                        enabled = !saving && !implied,
                        onToggle = { onToggle(permission) },
                    )
                }
            }
        }
        BingeSheetFooter(
            label = stringResource(R.string.users_edit_permissions_save),
            onClick = onSave,
            enabled = !saving,
            loading = saving,
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_xs)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
