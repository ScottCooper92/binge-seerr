package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemConnector
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.PermissionGroup
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle

/** One permission's place in the web client's tree: under a parent or not, and whether it ends that parent's run. */
internal data class PermissionNode(
    val permission: ManageablePermission,
    val child: Boolean = false,
    val last: Boolean = false,
)

/**
 * [offered] laid out as the web client's tree, group by group, leaving out a group with nothing in it: each root in
 * [ManageablePermission]'s order, which is the web client's, with its children under it. Manage settings is hidden as
 * the web client hides it, unless [selected] already holds it and so needs a way to take it off. A child whose parent
 * this server doesn't offer, a 4K media type with only its own 4K on, stands as a root.
 */
internal fun permissionTree(
    offered: List<ManageablePermission>,
    selected: Set<ManageablePermission>,
): List<Pair<PermissionGroup, List<PermissionNode>>> {
    val shown = offered.filter { it != ManageablePermission.ManageSettings || it in selected }.toSet()
    val roots = shown.filter { it.parent !in shown }
    return PermissionGroup.entries.mapNotNull { group ->
        val nodes =
            ManageablePermission.entries.filter { it in roots && it.group == group }.flatMap { root ->
                val children = ManageablePermission.entries.filter { it.parent == root && it in shown }
                listOf(PermissionNode(root)) +
                    children.mapIndexed { index, child -> PermissionNode(child, child = true, last = index == children.lastIndex) }
            }
        nodes.takeIf { it.isNotEmpty() }?.let { group to it }
    }
}

/**
 * One group's nodes as switch rows, children joined to their parent by the design system's connector. A permission a
 * selected one already covers reads on and can't be flipped, nor can one in [locked]: what the viewer may not grant.
 * One whose requirement isn't granted, an auto-approve without its request, reads off and can't be flipped, as the
 * web client shows it.
 */
@Composable
internal fun permissionRows(
    nodes: List<PermissionNode>,
    selected: Set<ManageablePermission>,
    saving: Boolean,
    locked: Set<ManageablePermission>,
    onToggle: (ManageablePermission) -> Unit,
): List<ListItem> =
    nodes.map { (permission, child, last) ->
        val implied = permission !in selected && ManageablePermission.isGranted(permission, selected)
        val unmet = !implied && !ManageablePermission.requirementsMet(permission, selected)
        editorToggle(
            Icons.Filled.Security,
            stringResource(permission.labelRes()),
            !unmet && (permission in selected || implied),
            !saving && !implied && !unmet && permission !in locked,
            detail = permission.detailRes()?.let { stringResource(it) },
        ) { onToggle(permission) }.copy(
            connector =
                when {
                    !child -> null
                    last -> ListItemConnector.End
                    else -> ListItemConnector.Continue
                },
        )
    }

/** A one-liner only where the name doesn't say what the permission lets someone do. */
private fun ManageablePermission.detailRes(): Int? =
    when (this) {
        ManageablePermission.Admin -> R.string.permission_admin_detail
        ManageablePermission.ManageRequests -> R.string.permission_manage_requests_detail
        ManageablePermission.RequestAdvanced -> R.string.permission_request_advanced_detail
        ManageablePermission.ViewRequests -> R.string.permission_view_requests_detail
        ManageablePermission.AutoApprove -> R.string.permission_auto_approve_detail
        ManageablePermission.AutoApprove4k -> R.string.permission_auto_approve_4k_detail
        ManageablePermission.ViewIssues -> R.string.permission_view_issues_detail
        ManageablePermission.AutoRequest -> R.string.permission_auto_request_detail
        ManageablePermission.ViewWatchlists -> R.string.permission_view_watchlists_detail
        else -> null
    }
