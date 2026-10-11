package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.UserStore
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.seerr.SeerrUserPermissionsBody
import com.cooper.binge.companion.seerr.seerr.permissionScope
import com.cooper.binge.companion.seerr.ui.users.OWNER_USER_ID
import com.cooper.binge.companion.seerr.ui.users.labelRes
import com.cooper.binge.companion.seerr.ui.users.mayChangeAsNonOwner
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

        /** Flips [permission], which saves on its own, and offers to flip it back (#940). */
        fun toggle(permission: ManageablePermission) {
            val before = ready()?.draft ?: return
            edit { it.toggled(permission) }
            val after = ready()?.draft ?: return
            if (after == before) return
            val wasOn = permission in before.selected
            val on = permission in after.selected
            val undoable =
                EditorEvent.Undoable(
                    messageRes = if (on) R.string.user_settings_permission_on else R.string.user_settings_permission_off,
                    argRes = permission.labelRes(),
                    undo = {
                        edit { it.copy(selected = if (wasOn) it.selected + permission else it.selected - permission) }
                    },
                )
            viewModelScope.launch { notify(undoable) }
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
