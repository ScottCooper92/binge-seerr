package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.OWNER_USER_ID
import io.github.scottcooper92.binge.seerr.ui.users.UserItem
import io.github.scottcooper92.binge.seerr.ui.users.toUserItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The index of a user's settings: which pages this viewer gets for this user. */
@HiltViewModel(assistedFactory = UserSettingsViewModel.Factory::class)
class UserSettingsViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val userId: Int,
    ) : ViewModel() {
        private val state = MutableStateFlow<UserSettingsUiState>(UserSettingsUiState.Loading)
        val uiState: StateFlow<UserSettingsUiState> = state.asStateFlow()

        init {
            reload()
        }

        fun reload() {
            state.value = UserSettingsUiState.Loading
            viewModelScope.launch(dispatcher) {
                runCatching { load() }
                    .onSuccess { state.value = UserSettingsUiState.Ready(it) }
                    .onFailure { state.value = UserSettingsUiState.Error(it.toSeerrError()) }
            }
        }

        private suspend fun load(): UserSettingsIndex =
            coroutineScope {
                val api = connection.api()
                val viewer = async { connection.authenticatedUser() }
                val profile = async { connection.profile() }
                val target = checkNotNull(api.user(userId).toUserItem()) { "A user with nothing to show" }
                UserSettingsIndex(userName = target.name, pages = settingsPagesFor(target, viewer.await(), profile.await()))
            }

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): UserSettingsViewModel
        }
    }

/**
 * The web client's own menu rules, in its order. Everything needs the viewer to be the user or a manager, and the
 * owner's settings are the owner's alone: the server refuses every save to user 1 from anyone else (#1005). The
 * password page goes when local sign-in is off and the viewer is not an admin, or when the server would refuse
 * the change: an admin's password is set only by that admin or by the owner. Linked accounts are the user's alone, on a
 * server that has them. Permissions are a manager's, and never one's own, the owner's included: the server refuses a
 * permissions write to oneself or to user 1 (#1006).
 */
internal fun settingsPagesFor(
    target: UserItem,
    viewer: SeerrUserDto,
    profile: SeerrServerProfile,
): List<UserSettingsPage> {
    val isSelf = viewer.id == target.id
    val permissions = viewer.toPermissions()
    if (!mayOpenSettings(target.id, viewer.id, permissions)) return emptyList()
    return buildList {
        add(UserSettingsPage.General)
        val localSignIn = profile.settings.localLogin || permissions.isAdmin
        val mayChangePassword = isSelf || viewer.id == OWNER_USER_ID || !target.isAdmin
        if (localSignIn && mayChangePassword) add(UserSettingsPage.Password)
        if (isSelf && profile.hasLinkedAccounts) add(UserSettingsPage.LinkedAccounts)
        add(UserSettingsPage.Notifications)
        if (permissions.canManageUsers && !isSelf) add(UserSettingsPage.Permissions)
    }
}

/** Whether [viewerId] may open user [targetId]'s settings at all: their own, or as a manager, and the owner's only as the owner. */
internal fun mayOpenSettings(
    targetId: Int,
    viewerId: Int?,
    permissions: SeerrPermissions,
): Boolean {
    if (targetId == OWNER_USER_ID && viewerId != OWNER_USER_ID) return false
    return viewerId == targetId || permissions.canManageUsers
}
