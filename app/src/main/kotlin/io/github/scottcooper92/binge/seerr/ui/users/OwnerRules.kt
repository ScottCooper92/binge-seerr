package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.seerr.isAdminBitmask
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserRole

/** What the web client calls a user: the server's first account, an administrator, or anyone else. */
internal fun userRole(
    id: Int?,
    isAdmin: Boolean,
): UserRole =
    when {
        id == OWNER_USER_ID -> UserRole.Owner
        isAdmin -> UserRole.Admin
        else -> UserRole.User
    }

/**
 * Whether a viewer who is not the owner may give user [id] the bitmask [permissions] (#1008). The server answers 403 to a
 * permissions write whose mask carries Admin from anyone but the owner (`canMakePermissionsChange`), and the bulk `PUT /user`
 * drops user 1 without a word, so neither is sent. The bulk edit skips such a user and still saves the rest; the per-user
 * page shows such a user read-only (#1134).
 */
internal fun mayChangeAsNonOwner(
    id: Int,
    permissions: Int,
): Boolean = id != OWNER_USER_ID && !isAdminBitmask(permissions)
