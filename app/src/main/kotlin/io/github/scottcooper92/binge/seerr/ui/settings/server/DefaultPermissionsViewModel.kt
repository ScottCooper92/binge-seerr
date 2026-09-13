package io.github.scottcooper92.binge.seerr.ui.settings.server

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsUpdateBody
import io.github.scottcooper92.binge.seerr.ui.users.OWNER_USER_ID
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.PermissionSettings
import io.github.scottcooper92.binge.seerr.ui.users.settings.lockedFor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * The permissions a new user starts with, as the same editor a user's own page uses, over the
 * `defaultPermissions` bits of the main settings. The write sends only those bits; the server
 * merges them over the rest of the record. A viewer may grant by default only what they hold.
 */
@HiltViewModel
class DefaultPermissionsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : EditorViewModel<PermissionSettings>() {
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
                    offered = ManageablePermission.offered(profile.await().hasBlocklist),
                    locked = lockedFor(ManageablePermission.decode(viewerDto.permissions ?: 0), isOwner = viewerDto.id == OWNER_USER_ID),
                )
            }

        override suspend fun write(draft: PermissionSettings): PermissionSettings {
            val bits = ManageablePermission.apply(draft.original, draft.selected)
            val answered = connection.api().updateMainSettings(SeerrMainSettingsUpdateBody(defaultPermissions = bits))
            val adopted = answered.defaultPermissions ?: bits
            return draft.copy(selected = ManageablePermission.decode(adopted), original = adopted)
        }

        fun toggle(permission: ManageablePermission) =
            edit { draft ->
                if (permission in draft.locked) return@edit draft
                draft.copy(selected = if (permission in draft.selected) draft.selected - permission else draft.selected + permission)
            }
    }
