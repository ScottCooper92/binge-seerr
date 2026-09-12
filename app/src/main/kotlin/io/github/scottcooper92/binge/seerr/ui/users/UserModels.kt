package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrError

/** The orders the server lists users in, each carrying its own `sort` value. */
enum class UserSort(
    val apiValue: String,
) {
    Created("created"),
    Updated("updated"),
    DisplayName("displayname"),
    Requests("requests"),
}

/** Which server a user signs in against, from Seerr's `userType`. */
enum class UserOrigin { Plex, Jellyfin, Emby, Local }

/**
 * One row of the browser: the name the server shows, the handle and server they sign in with,
 * their role, and how many requests they have made. [permissions] is the raw bitmask.
 */
data class UserItem(
    val id: Int,
    val name: String,
    val email: String?,
    val handle: String?,
    val avatarUrl: String?,
    val origin: UserOrigin,
    val permissions: Int,
    val requestCount: Int,
    val createdAtMillis: Long?,
) {
    val isAdmin: Boolean get() = ManageablePermission.Admin in ManageablePermission.decode(permissions)
}

/** The bulk editor while it is open: one permission set to write to every selected user. */
data class BulkEdit(
    val selected: Set<ManageablePermission> = emptySet(),
    val saving: Boolean = false,
)

sealed interface UsersUiState {
    data object Loading : UsersUiState

    data class Ready(
        val sort: UserSort,
        /** The rows ticked for a bulk edit; empty when not selecting. */
        val selection: Set<Int>,
        val edit: BulkEdit?,
        /** The toggles the editor offers: the blocklist ones only on the Jellyseerr lineage. */
        val offered: List<ManageablePermission>,
        /** Where a row opens until the user page lands. */
        val baseUrl: String?,
    ) : UsersUiState
}

sealed interface UsersEvent {
    data class PermissionsSaved(
        val count: Int,
    ) : UsersEvent

    data class Failed(
        val error: SeerrError,
    ) : UsersEvent
}
