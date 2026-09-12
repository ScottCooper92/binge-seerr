package io.github.scottcooper92.binge.seerr.ui.users.settings

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.UserStore
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserPermissionsBody
import io.github.scottcooper92.binge.seerr.ui.users.OWNER_USER_ID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The permissions page for one user: the same toggles as the browser's bulk edit, over the
 * server's record of this user. A save also lands on the cached browser row, so the list agrees
 * without a refresh.
 */
@HiltViewModel(assistedFactory = PermissionsViewModel.Factory::class)
class PermissionsViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val store: UserStore,
        @Assisted private val userId: Int,
    ) : EditorViewModel<PermissionSettings>() {
        init {
            reload()
        }

        override suspend fun load(): PermissionSettings =
            coroutineScope {
                val api = connection.api()
                val viewer = async { connection.authenticatedUser() }
                val profile = async { connection.profile() }
                val record = api.userPermissions(userId)
                val viewerDto = viewer.await()
                val held = ManageablePermission.decode(viewerDto.permissions ?: 0)
                PermissionSettings(
                    selected = ManageablePermission.decode(record.permissions),
                    original = record.permissions,
                    offered = ManageablePermission.offered(profile.await().hasBlocklist),
                    locked = lockedFor(held, isOwner = viewerDto.id == OWNER_USER_ID),
                )
            }

        override suspend fun write(draft: PermissionSettings): PermissionSettings {
            val body = SeerrUserPermissionsBody(ManageablePermission.apply(draft.original, draft.selected))
            val record = connection.api().updateUserPermissions(userId, body)
            store.updatePermissions(listOf(userId), record.permissions)
            return draft.copy(selected = ManageablePermission.decode(record.permissions), original = record.permissions)
        }

        fun toggle(permission: ManageablePermission) =
            edit { draft ->
                if (permission in draft.locked) return@edit draft
                draft.copy(selected = if (permission in draft.selected) draft.selected - permission else draft.selected + permission)
            }

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): PermissionsViewModel
        }
    }

/** A viewer may grant only what they hold, and only the owner may grant or revoke Admin. */
internal fun lockedFor(
    held: Set<ManageablePermission>,
    isOwner: Boolean,
): Set<ManageablePermission> =
    ManageablePermission.entries.filterTo(mutableSetOf()) { permission ->
        !ManageablePermission.isGranted(permission, held) || (permission == ManageablePermission.Admin && !isOwner)
    }
