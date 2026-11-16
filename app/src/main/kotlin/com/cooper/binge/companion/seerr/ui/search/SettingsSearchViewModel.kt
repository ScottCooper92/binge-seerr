package com.cooper.binge.companion.seerr.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.toPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The search page: it waits for who the viewer is and what the server has, which decide what it may offer. */
internal sealed interface SettingsSearchUiState {
    data object Loading : SettingsSearchUiState

    data class Ready(
        val access: SearchAccess,
    ) : SettingsSearchUiState

    data object Error : SettingsSearchUiState
}

/**
 * The settings search's reads: the signed-in user, from the connection's own copy so a user read up to a minute old is reused, and
 * the server's profile. Nothing is fetched that the hub has not already read, and nothing here writes.
 */
@HiltViewModel
internal class SettingsSearchViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        private val state = MutableStateFlow<SettingsSearchUiState>(SettingsSearchUiState.Loading)
        val uiState: StateFlow<SettingsSearchUiState> = state.asStateFlow()

        init {
            load()
        }

        fun retry() {
            state.value = SettingsSearchUiState.Loading
            load()
        }

        private fun load() {
            viewModelScope.launch {
                val user = attempt { connection.recentAuthenticatedUser() }.getOrNull()
                val profile = attempt { connection.profile() }.getOrNull()
                state.value =
                    if (user == null || profile == null) {
                        SettingsSearchUiState.Error
                    } else {
                        SettingsSearchUiState.Ready(SearchAccess(user.id, user.toPermissions().isAdmin, profile))
                    }
            }
        }
    }
