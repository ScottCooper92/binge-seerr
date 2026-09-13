package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.OWNER_USER_ID
import io.github.scottcooper92.binge.seerr.ui.users.UserItem
import io.github.scottcooper92.binge.seerr.ui.users.toUserItem
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
        @Assisted private val userId: Int,
    ) : ViewModel() {
        private val state = MutableStateFlow<UserSettingsUiState>(UserSettingsUiState.Loading)
        val uiState: StateFlow<UserSettingsUiState> = state.asStateFlow()

        init {
            reload()
        }

        fun reload() {
            state.value = UserSettingsUiState.Loading
            viewModelScope.launch {
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
 * The web client's own menu rules. Everything needs the viewer to be the user or a manager. The
 * password page goes when local sign-in is off and the viewer cannot manage settings, or when the
 * target is an admin the viewer is not. Permissions are a manager's, and never one's own unless
 * the viewer is the owner. Linked accounts are the user's alone, on a server that has them.
 */
internal fun settingsPagesFor(
    target: UserItem,
    viewer: SeerrUserDto,
    profile: SeerrServerProfile,
): List<UserSettingsPage> {
    val isSelf = viewer.id == target.id
    val permissions = viewer.toPermissions()
    if (!isSelf && !permissions.canManageUsers) return emptyList()
    return buildList {
        add(UserSettingsPage.General)
        val localSignIn = profile.settings.localLogin || permissions.canManageSettings
        if (localSignIn && (isSelf || !target.isAdmin || permissions.isAdmin)) add(UserSettingsPage.Password)
        add(UserSettingsPage.Notifications)
        if (permissions.canManageUsers && (!isSelf || viewer.id == OWNER_USER_ID)) add(UserSettingsPage.Permissions)
        if (isSelf && profile.hasLinkedAccounts) add(UserSettingsPage.LinkedAccounts)
    }
}
