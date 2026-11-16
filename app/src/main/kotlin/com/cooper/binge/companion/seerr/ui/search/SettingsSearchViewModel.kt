package com.cooper.binge.companion.seerr.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toSeerrError
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

    data class Error(
        val error: SeerrError,
    ) : SettingsSearchUiState
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
                attempt {
                    val user = connection.recentAuthenticatedUser()
                    SearchAccess(user.id, user.toPermissions().isAdmin, connection.profile())
                }.onSuccess { state.value = SettingsSearchUiState.Ready(it) }
                    .onFailure { state.value = SettingsSearchUiState.Error(it.toSeerrError()) }
            }
        }
    }
