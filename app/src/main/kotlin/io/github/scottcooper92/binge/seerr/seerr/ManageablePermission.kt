package io.github.scottcooper92.binge.seerr.seerr

/** How the editor groups the toggles; the order is the order shown. */
enum class PermissionGroup { Administration, Requests, Issues, Blocklist }

/**
 * The permissions an editor can flip, each with its server bit (`server/lib/permissions.ts`).
 * [Admin] implies every other one, so the editor shows the rest as granted and locked while it is
 * on; [Request] likewise covers the 4K and advanced grants beneath it. Bits the server has that
 * are not listed here are preserved untouched on save: the editor only ever changes these.
 */
enum class ManageablePermission(
    val bit: Int,
    val group: PermissionGroup,
    /** Only the Jellyseerr lineage has the bit; on Overseerr the toggle is not shown. */
    val jellyseerrOnly: Boolean = false,
) {
    Admin(PERMISSION_ADMIN, PermissionGroup.Administration),
    ManageSettings(PERMISSION_MANAGE_SETTINGS, PermissionGroup.Administration),
    ManageUsers(PERMISSION_MANAGE_USERS, PermissionGroup.Administration),
    ManageRequests(PERMISSION_MANAGE_REQUESTS, PermissionGroup.Requests),
    ViewRequests(PERMISSION_REQUEST_VIEW, PermissionGroup.Requests),
    Request(PERMISSION_REQUEST, PermissionGroup.Requests),
    Request4k(PERMISSION_REQUEST_4K, PermissionGroup.Requests),
    RequestAdvanced(PERMISSION_REQUEST_ADVANCED, PermissionGroup.Requests),
    AutoApprove(PERMISSION_AUTO_APPROVE, PermissionGroup.Requests),
    AutoApprove4k(PERMISSION_AUTO_APPROVE_4K, PermissionGroup.Requests),
    ManageIssues(PERMISSION_MANAGE_ISSUES, PermissionGroup.Issues),
    ViewIssues(PERMISSION_VIEW_ISSUES, PermissionGroup.Issues),
    CreateIssues(PERMISSION_CREATE_ISSUES, PermissionGroup.Issues),
    ManageBlocklist(PERMISSION_MANAGE_BLOCKLIST, PermissionGroup.Blocklist, jellyseerrOnly = true),
    ViewBlocklist(PERMISSION_VIEW_BLOCKLIST, PermissionGroup.Blocklist, jellyseerrOnly = true),
    ;

    /** The one whose grant already covers this, so the toggle reads on and locked while it is selected. */
    val impliedBy: ManageablePermission?
        get() =
            when (this) {
                Admin -> null
                Request4k, RequestAdvanced, AutoApprove, AutoApprove4k -> Request
                ViewRequests -> ManageRequests
                ViewIssues, CreateIssues -> ManageIssues
                ViewBlocklist -> ManageBlocklist
                else -> Admin
            }

    companion object {
        val managedMask: Int = entries.fold(0) { acc, permission -> acc or permission.bit }

        /** The toggles a server of this lineage offers. */
        fun offered(jellyseerrLineage: Boolean): List<ManageablePermission> = entries.filter { jellyseerrLineage || !it.jellyseerrOnly }

        /** The editable permissions a raw bitmask has set; [Admin] is read as its own bit, not as everything. */
        fun decode(bitmask: Int): Set<ManageablePermission> = entries.filterTo(mutableSetOf()) { bitmask and it.bit != 0 }

        /** Re-applies [selected] onto [original], keeping every bit the editor does not manage. */
        fun apply(
            original: Int,
            selected: Set<ManageablePermission>,
        ): Int = (original and managedMask.inv()) or selected.fold(0) { acc, permission -> acc or permission.bit }

        /** Whether [permission] reads as granted under [selected]: its own bit, or one that implies it. */
        fun isGranted(
            permission: ManageablePermission,
            selected: Set<ManageablePermission>,
        ): Boolean = permission in selected || Admin in selected || permission.impliedBy?.let { isGranted(it, selected) } == true
    }
}
