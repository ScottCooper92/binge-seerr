package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPageActionBar
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSwitchRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.LocalEditorPageInsets
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
    /** Toggles shown but not flippable: what the viewer may not grant. */
    locked: Set<ManageablePermission> = emptySet(),
    /**
     * False from the full-screen page, whose own [EditorPageActionBar] carries Save instead - true
     * here still adds a sheet's own bottom breathing room on top of [LocalEditorPageInsets], which a
     * sheet has none of to begin with.
     */
    showFooter: Boolean = true,
) {
    // Zero from a sheet (no EditorPage above to provide it - a sheet has its own window chrome), the
    // top/bottom bar insets from the full-screen page this same content also serves as
    // EditorPage(scrolling = false)'s body, which leaves them for content to fold in itself. Neither
    // the title nor the footer scroll - only the middle list does - so a plain Modifier.padding here
    // is the right shape, unlike the sliders screen's own contentPadding on its own scrollable.
    val insets = LocalEditorPageInsets.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    top = insets.calculateTopPadding(),
                    bottom =
                        insets.calculateBottomPadding() +
                            dimensionResource(if (showFooter) DesR.dimen.padding_l else DesR.dimen.zero),
                ),
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
                    EditorSwitchRow(
                        label = stringResource(permission.labelRes()),
                        checked = permission in selected || implied,
                        enabled = !saving && !implied && permission !in locked,
                        contentPadding =
                            PaddingValues(
                                horizontal = dimensionResource(DesR.dimen.padding_m),
                                vertical = dimensionResource(DesR.dimen.padding_xs),
                            ),
                        onToggle = { onToggle(permission) },
                    )
                }
            }
        }
        if (showFooter) {
            BingeActionFooter(
                label = stringResource(R.string.users_edit_permissions_save),
                onClick = onSave,
                enabled = !saving,
                loading = saving,
                modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
            )
        }
    }
}
