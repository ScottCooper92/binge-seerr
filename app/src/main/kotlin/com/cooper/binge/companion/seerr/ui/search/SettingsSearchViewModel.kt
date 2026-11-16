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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The search page: it waits for who the viewer is and what the server has, which decide what it may offer. */
internal sealed interface SettingsSearchUiState {
    data object Loading : SettingsSearchUiState

    /** [recent] is what the viewer last opened from here that they may still open, newest first. */
    data class Ready(
        val access: SearchAccess,
        val recent: List<SettingEntry> = emptyList(),
    ) : SettingsSearchUiState

    data class Error(
        val error: SeerrError,
    ) : SettingsSearchUiState
}

/**
 * The settings search's reads: the signed-in user, from the connection's own copy so a user read up to a minute old is reused, and
 * the server's profile. Nothing is fetched that the hub has not already read. The only thing it writes is [RecentSearches], on this
 * device.
 */
@HiltViewModel
internal class SettingsSearchViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val recents: RecentSearches,
    ) : ViewModel() {
        private val loaded = MutableStateFlow<SettingsSearchUiState>(SettingsSearchUiState.Loading)

        val uiState: StateFlow<SettingsSearchUiState> =
            combine(loaded, recents.ids) { load, ids ->
                if (load is SettingsSearchUiState.Ready) load.copy(recent = recentEntries(load.access, ids)) else load
            }.stateIn(viewModelScope, SharingStarted.Lazily, SettingsSearchUiState.Loading)

        init {
            load()
        }

        fun retry() {
            loaded.value = SettingsSearchUiState.Loading
            load()
        }

        /** Remembers [entry], which the user is opening, so it leads the recents next time. */
        fun remember(entry: SettingEntry) {
            viewModelScope.launch { recents.remember(entry.id) }
        }

        fun clearRecent() {
            viewModelScope.launch { recents.clear() }
        }

        private fun load() {
            viewModelScope.launch {
                attempt {
                    val user = connection.recentAuthenticatedUser()
                    val profile = connection.profile()
                    SearchAccess(user.id, user.toPermissions().isAdmin, profile)
                }.onSuccess { loaded.value = SettingsSearchUiState.Ready(it) }
                    .onFailure { loaded.value = SettingsSearchUiState.Error(it.toSeerrError()) }
            }
        }
    }

/** The entries [ids] name that the viewer may open now, in the order given: one the app dropped, or the viewer lost, is left out. */
private fun recentEntries(
    access: SearchAccess,
    ids: List<String>,
): List<SettingEntry> {
    val byId = settingsIndex.filter { it.available(access) }.associateBy { it.id }
    return ids.mapNotNull(byId::get)
}
