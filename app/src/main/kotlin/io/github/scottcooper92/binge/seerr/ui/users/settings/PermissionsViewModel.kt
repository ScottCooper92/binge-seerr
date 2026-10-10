package io.github.scottcooper92.binge.seerr.ui.users.settings

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.UserStore
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserPermissionsBody
import io.github.scottcooper92.binge.seerr.seerr.permissionScope
import io.github.scottcooper92.binge.seerr.ui.users.OWNER_USER_ID
import io.github.scottcooper92.binge.seerr.ui.users.mayChangeAsNonOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The permissions page for one user: the same toggles as the browser's bulk edit, over the
 * server's record of this user. A save also lands on the cached browser row, so the list agrees
 * without a refresh. An admin's page is read-only to anyone but the owner, as the server's rule is.
 */
@HiltViewModel(assistedFactory = PermissionsViewModel.Factory::class)
class PermissionsViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val store: UserStore,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        @ApplicationScope appScope: CoroutineScope,
        @Assisted private val userId: Int,
    ) : EditorViewModel<PermissionSettings>(dispatcher) {
        /** A user's permissions save as they change (#930): each switch is small and can be flipped back. */
        override val saveAsMadeScope: CoroutineScope = appScope

        init {
            reload()
        }

        override suspend fun load(): PermissionSettings =
            coroutineScope {
                val api = connection.api()
                val viewer = async { connection.authenticatedUser() }
                val profile = async { connection.profile() }
                val record = api.userPermissions(userId)
                val isOwner = viewer.await().id == OWNER_USER_ID
                val ownerOnly = !isOwner && !mayChangeAsNonOwner(userId, record.permissions)
                PermissionSettings(
                    selected = ManageablePermission.decode(record.permissions),
                    original = record.permissions,
                    offered = ManageablePermission.offered(profile.await().permissionScope()),
                    locked = if (ownerOnly) ManageablePermission.entries.toSet() else lockedFor(isOwner),
                    ownerOnly = ownerOnly,
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

/**
 * The toggles a viewer may not flip: Admin, for anyone but the owner. That is the server's one rule on a permissions
 * write (`canMakePermissionsChange`), and the web client's. A manager may grant any other permission, held or not (#1016).
 */
internal fun lockedFor(isOwner: Boolean): Set<ManageablePermission> = if (isOwner) emptySet() else setOf(ManageablePermission.Admin)
