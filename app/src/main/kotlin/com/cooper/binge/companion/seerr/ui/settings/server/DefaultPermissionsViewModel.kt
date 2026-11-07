package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.seerr.SeerrMainSettingsUpdateBody
import com.cooper.binge.companion.seerr.seerr.permissionScope
import com.cooper.binge.companion.seerr.ui.users.OWNER_USER_ID
import com.cooper.binge.companion.seerr.ui.users.settings.EditorViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.PermissionSettings
import com.cooper.binge.companion.seerr.ui.users.settings.lockedFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * The permissions a new user starts with, as the same editor a user's own page uses, over the
 * `defaultPermissions` bits of the main settings. The write sends only those bits; the server
 * merges them over the rest of the record. As on a user's own page, only the owner may set Admin (#1016).
 */
@HiltViewModel
class DefaultPermissionsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        @ApplicationScope appScope: CoroutineScope,
    ) : EditorViewModel<PermissionSettings>(dispatcher) {
        /**
         * The page saves as it changes, as a user's own permissions do (#930): its screen hides Save, so without this a
         * switch moved on it was never sent (#1877).
         */
        override val saveAsMadeScope: CoroutineScope = appScope

        init {
            reload()
        }

        override suspend fun load(): PermissionSettings =
            coroutineScope {
                val api = connection.api()
                val viewer = async { connection.authenticatedUser() }
                val profile = async { connection.profile() }
                val bits = api.mainSettings().defaultPermissions ?: 0
                val viewerDto = viewer.await()
                PermissionSettings(
                    selected = ManageablePermission.decode(bits),
                    original = bits,
                    offered = ManageablePermission.offered(profile.await().permissionScope()),
                    locked = lockedFor(isOwner = viewerDto.id == OWNER_USER_ID),
                )
            }

        override suspend fun write(draft: PermissionSettings): PermissionSettings {
            val bits = ManageablePermission.apply(draft.original, draft.selected)
            val answered = connection.api().updateMainSettings(SeerrMainSettingsUpdateBody(defaultPermissions = bits))
            val adopted = answered.defaultPermissions ?: bits
            return draft.copy(selected = ManageablePermission.decode(adopted), original = adopted)
        }

        fun toggle(permission: ManageablePermission) = edit { it.toggled(permission) }
    }
