package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the about page shows: the edition and its update state from the profile, and the totals,
 * timezone and data directory from the admin's reads. [appDataMounted] and [appDataWritable] are
 * null where the server did not say; the web client warns when either is false.
 */
data class AboutInfo(
    val variant: SeerrVariant,
    val versionLabel: String?,
    val commitTag: String?,
    val updateAvailable: Boolean,
    val commitsBehind: Int,
    val totalRequests: Int?,
    val totalMediaItems: Int?,
    val timezone: String?,
    val appDataPath: String?,
    val appDataMounted: Boolean?,
    val appDataWritable: Boolean?,
    /** How the totals and timezone read went. */
    val aboutRead: AboutRead = AboutRead.Read,
    /** How the data directory read went. */
    val appDataRead: AboutRead = AboutRead.Read,
) {
    val appDataWarning: Boolean get() = appDataMounted == false || appDataWritable == false
}

sealed interface AboutUiState {
    data object Loading : AboutUiState

    data class Error(
        val error: SeerrError,
    ) : AboutUiState

    data class Ready(
        val info: AboutInfo,
        /** A pull is re-reading the page; the last content stays in place meanwhile. */
        val isRefreshing: Boolean = false,
    ) : AboutUiState
}

/** What the about page tells its screen beside its state. */
sealed interface AboutEvent {
    /** A pull could not re-read the page, which stays as it was. */
    data class RefreshFailed(
        val error: SeerrError,
    ) : AboutEvent
}

/** The about page: the profile for everyone, the totals and the data volume where the admin's reads are allowed. */
@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val state = MutableStateFlow<AboutUiState>(AboutUiState.Loading)
        val uiState: StateFlow<AboutUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<AboutEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<AboutEvent> = eventFlow.asSharedFlow()

        init {
            reload()
        }

        /** A pull: re-reads the page and swaps it in, keeping what shows until then. A pull whose profile read fails leaves the page as it is. */
        fun refresh() {
            val ready = state.value as? AboutUiState.Ready ?: return
            if (ready.isRefreshing) return
            state.value = ready.copy(isRefreshing = true)
            viewModelScope.launch(dispatcher) {
                attempt { load(earlier = ready.info, freshProfile = true) }
                    .onSuccess { state.value = AboutUiState.Ready(it) }
                    .onFailure {
                        state.update { current -> (current as? AboutUiState.Ready)?.copy(isRefreshing = false) ?: current }
                        eventFlow.tryEmit(AboutEvent.RefreshFailed(it.toSeerrError()))
                    }
            }
        }

        fun reload() {
            state.value = AboutUiState.Loading
            viewModelScope.launch(dispatcher) {
                attempt { load() }
                    .onSuccess { state.value = AboutUiState.Ready(it) }
                    .onFailure { state.value = AboutUiState.Error(it.toSeerrError()) }
            }
        }

        /** [freshProfile] re-reads the server's profile, which is otherwise held for the life of the connection: a pull wants its update state. */
        private suspend fun load(
            earlier: AboutInfo? = null,
            freshProfile: Boolean = false,
        ): AboutInfo =
            coroutineScope {
                val api = connection.api()
                val about = async { attempt { api.about() } }
                val appData = async { attempt { api.appData() } }
                val profile = if (freshProfile) connection.refreshProfileOrFail().getOrThrow() else connection.profile()
                val aboutResult = about.await()
                val appDataResult = appData.await()
                val aboutDto = aboutResult.getOrNull()
                val appDataDto = appDataResult.getOrNull()
                AboutInfo(
                    variant = profile.variant,
                    versionLabel = profile.version?.label ?: aboutDto?.version?.takeIf { it.isNotBlank() },
                    commitTag = profile.commitTag?.takeIf { it.isNotBlank() },
                    updateAvailable = profile.updateAvailable || profile.commitsBehind > 0,
                    commitsBehind = profile.commitsBehind,
                    totalRequests = aboutDto?.totalRequests,
                    totalMediaItems = aboutDto?.totalMediaItems,
                    timezone = aboutDto?.tz?.takeIf { it.isNotBlank() },
                    appDataPath = (appDataDto?.appDataPath ?: aboutDto?.appDataPath)?.takeIf { it.isNotBlank() },
                    appDataMounted = appDataDto?.appData,
                    appDataWritable = appDataDto?.appDataPermissions,
                    aboutRead = aboutResult.toAboutRead(),
                    appDataRead = appDataResult.toAboutRead(),
                ).keepingEarlier(earlier)
            }
    }
