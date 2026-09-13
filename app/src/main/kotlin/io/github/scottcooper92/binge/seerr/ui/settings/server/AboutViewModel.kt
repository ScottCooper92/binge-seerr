package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) : AboutUiState
}

/** The about page: the profile for everyone, the totals and the data volume where the admin's reads are allowed. */
@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        private val state = MutableStateFlow<AboutUiState>(AboutUiState.Loading)
        val uiState: StateFlow<AboutUiState> = state.asStateFlow()

        init {
            reload()
        }

        fun reload() {
            state.value = AboutUiState.Loading
            viewModelScope.launch {
                runCatching { load() }
                    .onSuccess { state.value = AboutUiState.Ready(it) }
                    .onFailure { state.value = AboutUiState.Error(it.toSeerrError()) }
            }
        }

        private suspend fun load(): AboutInfo =
            coroutineScope {
                val api = connection.api()
                val about = async { runCatching { api.about() }.getOrNull() }
                val appData = async { runCatching { api.appData() }.getOrNull() }
                val profile = connection.profile()
                val aboutDto = about.await()
                val appDataDto = appData.await()
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
                )
            }
    }
