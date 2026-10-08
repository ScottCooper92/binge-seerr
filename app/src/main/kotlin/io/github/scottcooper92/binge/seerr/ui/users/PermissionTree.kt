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
 * The web client's permission tree over the permissions this app manages, in its order: each group's roots, each
 * root's children under it. The web client nests the children because their parent covers them.
 */
private val PERMISSION_TREE: List<Pair<PermissionGroup, List<Pair<ManageablePermission, List<ManageablePermission>>>>> =
    listOf(
        PermissionGroup.Administration to
            listOf(
                ManageablePermission.Admin to emptyList(),
                ManageablePermission.ManageSettings to emptyList(),
                ManageablePermission.ManageUsers to emptyList(),
            ),
        PermissionGroup.Requests to
            listOf(
                ManageablePermission.ManageRequests to listOf(ManageablePermission.RequestAdvanced, ManageablePermission.ViewRequests),
                ManageablePermission.Request to emptyList(),
                ManageablePermission.AutoApprove to emptyList(),
                ManageablePermission.Request4k to emptyList(),
                ManageablePermission.AutoApprove4k to emptyList(),
            ),
        PermissionGroup.Issues to
            listOf(ManageablePermission.ManageIssues to listOf(ManageablePermission.CreateIssues, ManageablePermission.ViewIssues)),
        PermissionGroup.Blocklist to
            listOf(ManageablePermission.ManageBlocklist to listOf(ManageablePermission.ViewBlocklist)),
    )

/**
 * [offered] laid out as the web client's tree, group by group, leaving out a group with nothing in it. Manage settings
 * is hidden as the web client hides it, unless [selected] already holds it and so needs a way to take it off. A
 * permission the tree doesn't place yet still shows, as a root at the end of its group.
 */
internal fun permissionTree(
    offered: List<ManageablePermission>,
    selected: Set<ManageablePermission>,
): List<Pair<PermissionGroup, List<PermissionNode>>> {
    val shown = offered.filter { it != ManageablePermission.ManageSettings || it in selected }.toSet()
    val placed = PERMISSION_TREE.flatMap { (_, roots) -> roots.flatMap { (root, children) -> listOf(root) + children } }.toSet()
    return PermissionGroup.entries.mapNotNull { group ->
        val roots = PERMISSION_TREE.firstOrNull { it.first == group }?.second.orEmpty()
        val nodes =
            roots.flatMap { (root, children) ->
                val kept = children.filter { it in shown }
                listOfNotNull(PermissionNode(root).takeIf { root in shown }) +
                    kept.mapIndexed { index, child -> PermissionNode(child, child = true, last = index == kept.lastIndex) }
            } + shown.filter { it.group == group && it !in placed }.map { PermissionNode(it) }
        nodes.takeIf { it.isNotEmpty() }?.let { group to it }
    }
}

/**
 * One group's nodes as switch rows, children joined to their parent by the design system's connector. A permission a
 * selected one already covers reads on and can't be flipped, nor can one in [locked]: what the viewer may not grant.
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
        editorToggle(
            Icons.Filled.Security,
            stringResource(permission.labelRes()),
            permission in selected || implied,
            !saving && !implied && permission !in locked,
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
        else -> null
    }
