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
    // Zero from a BingeBottomSheet caller, which sits outside EditorPage and needs no bar inset of
    // its own; a scrolling = false EditorPage caller (PermissionsSettingsScreen) has none of its own
    // top/bottom padding, so this content has to fold the bars' inset in itself. The top inset goes
    // onto the scrollable Column's own content, after its verticalScroll(), with the title folded in
    // as that scrollable's first item - the same shape LocalEditorPageInsets' KDoc requires and
    // DiscoverSlidersScreen already uses, so the list can scroll fully under the transparent top bar
    // rather than stopping dead at its edge. The footer below never scrolls, so when it is shown its
    // bottom inset is a plain margin instead - the same thing EditorPageActionBar gets from
    // navigationBarsPadding(). When there is no footer (the full-screen page, whose own
    // EditorPageActionBar already clears that inset for its bottomBar slot), the bottom inset folds
    // into the scrollable list itself instead, exactly like DiscoverSlidersScreen's own list does.
    val insets = LocalEditorPageInsets.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = insets.calculateTopPadding(),
                        bottom = if (showFooter) dimensionResource(DesR.dimen.zero) else insets.calculateBottomPadding(),
                    ),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                        .padding(bottom = dimensionResource(DesR.dimen.padding_s)),
            )
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
                modifier =
                    Modifier
                        .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                        .padding(bottom = insets.calculateBottomPadding() + dimensionResource(DesR.dimen.padding_l)),
            )
        }
    }
}
