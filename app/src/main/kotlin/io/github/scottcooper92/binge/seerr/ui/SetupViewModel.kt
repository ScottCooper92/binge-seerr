package io.github.scottcooper92.binge.seerr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.InvalidServerUrlException
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.isInsecurePublicUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/** How the user authenticates on the setup form. */
enum class AuthMode { ApiKey, Jellyfin, Local }

/** Why a connect or login attempt failed, as the setup form shows it. */
enum class SetupError { InvalidUrl, Rejected, Unreachable, Unknown }

/** The setup form's fields. Passwords and keys live here only until the attempt finishes. */
data class SetupForm(
    val serverUrl: String = "",
    val mode: AuthMode = AuthMode.ApiKey,
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
) {
    val insecure: Boolean get() = serverUrl.isInsecurePublicUrl()

    val canSubmit: Boolean
        get() =
            serverUrl.isNotBlank() &&
                when (mode) {
                    AuthMode.ApiKey -> apiKey.isNotBlank()
                    AuthMode.Jellyfin, AuthMode.Local -> username.isNotBlank() && password.isNotBlank()
                }
}

/** What the single screen shows: nothing decided yet, the form, or the saved connection. */
sealed interface SetupUiState {
    data object Loading : SetupUiState

    data class Disconnected(
        val form: SetupForm,
        val isConnecting: Boolean,
        val error: SetupError?,
    ) : SetupUiState

    data class Connected(
        val credentials: SeerrCredentials,
        val isDisconnecting: Boolean,
    ) : SetupUiState
}

/**
 * The one screen's state: the saved connection when there is one, otherwise the form. The
 * attempt's outcome is classified for the user — a wrong key, an unreachable server and a
 * malformed address each want a different next step.
 */
@HiltViewModel
class SetupViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        private val form = MutableStateFlow(SetupForm())
        private val busy = MutableStateFlow(false)
        private val error = MutableStateFlow<SetupError?>(null)

        val uiState: StateFlow<SetupUiState> =
            combine(connection.credentials, form, busy, error) { saved, form, busy, error ->
                if (saved != null) {
                    SetupUiState.Connected(saved, isDisconnecting = busy)
                } else {
                    SetupUiState.Disconnected(form, isConnecting = busy, error = error)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SetupUiState.Loading)

        fun edit(transform: SetupForm.() -> SetupForm) {
            form.value = form.value.transform()
            error.value = null
        }

        fun connect() {
            val current = form.value
            if (!current.canSubmit || busy.value) return
            busy.value = true
            error.value = null
            viewModelScope.launch {
                val result =
                    when (current.mode) {
                        AuthMode.ApiKey -> connection.connect(current.serverUrl, SeerrAuth.ApiKey(current.apiKey.trim()))
                        AuthMode.Jellyfin ->
                            connection.logIn(
                                current.serverUrl,
                                SeerrLoginRequest.Jellyfin(current.username.trim(), current.password),
                            )
                        AuthMode.Local ->
                            connection.logIn(
                                current.serverUrl,
                                SeerrLoginRequest.Local(current.username.trim(), current.password),
                            )
                    }
                result
                    // The secret is not kept on the form once it is stored encrypted.
                    .onSuccess { form.value = SetupForm(serverUrl = current.serverUrl, mode = current.mode) }
                    .onFailure { error.value = it.toSetupError() }
                busy.value = false
            }
        }

        fun disconnect() {
            if (busy.value) return
            busy.value = true
            viewModelScope.launch {
                connection.disconnect()
                busy.value = false
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** A 401/403 is the credentials; anything HTTP-shaped otherwise is still a server that answered. */
private fun Throwable.toSetupError(): SetupError =
    when (this) {
        is InvalidServerUrlException -> SetupError.InvalidUrl
        is HttpException -> if (code() == HTTP_UNAUTHORIZED || code() == HTTP_FORBIDDEN) SetupError.Rejected else SetupError.Unknown
        is IOException -> SetupError.Unreachable
        else -> SetupError.Unknown
    }

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
