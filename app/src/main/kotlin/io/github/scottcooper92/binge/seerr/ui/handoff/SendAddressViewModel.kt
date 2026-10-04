package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.handoff.AddressSender
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffLinks
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The phone's confirmation before it sends the connected server's address to a television (#323). */
sealed interface SendAddressUiState {
    data object Loading : SendAddressUiState

    /** The link is not one a television on this network wrote, so nothing is sent anywhere. */
    data object Refused : SendAddressUiState

    /** This phone has no server to send. */
    data object NotConnected : SendAddressUiState

    /** [serverAddress] is all that would be sent, and [tv] is where. */
    data class Ready(
        val serverAddress: String,
        val tv: String,
        val isSending: Boolean,
        val failed: Boolean,
    ) : SendAddressUiState

    data class Sent(
        val tv: String,
    ) : SendAddressUiState
}

/**
 * Reads a hand-off link and, once the user confirms, sends the connected server's address — and
 * only that — to the television that showed the code.
 *
 * The link is refused unless it names a private IPv4 address on the user's network
 * ([TvHandOffTarget.isOnLan]): a link is just text a web page can write, and this is the check
 * that stops one from pointing the phone anywhere else. The confirmation shows exactly what goes
 * where, and what goes is the base URL with any user info stripped, never a key, cookie or token
 * of the Seerr session.
 */
@HiltViewModel(assistedFactory = SendAddressViewModel.Factory::class)
class SendAddressViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val sender: AddressSender,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted link: String?,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SendAddressUiState>(SendAddressUiState.Loading)
        val uiState: StateFlow<SendAddressUiState> = _uiState.asStateFlow()

        private val target: TvHandOffTarget? = TvHandOffLinks.parse(link)?.takeIf { it.isOnLan }

        init {
            viewModelScope.launch(dispatcher) { load() }
        }

        private suspend fun load() {
            val target = target ?: return _uiState.update { SendAddressUiState.Refused }
            val address =
                connection.credentials
                    .first()
                    ?.baseUrl
                    ?.shareableAddress()
            _uiState.value =
                if (address == null) {
                    SendAddressUiState.NotConnected
                } else {
                    SendAddressUiState.Ready(serverAddress = address, tv = target.host, isSending = false, failed = false)
                }
        }

        fun send() {
            val ready = _uiState.value as? SendAddressUiState.Ready ?: return
            val target = target ?: return
            if (ready.isSending) return
            _uiState.value = ready.copy(isSending = true, failed = false)
            viewModelScope.launch(dispatcher) {
                _uiState.value =
                    if (sender.send(target, ready.serverAddress)) {
                        SendAddressUiState.Sent(tv = ready.tv)
                    } else {
                        ready.copy(isSending = false, failed = true)
                    }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(link: String?): SendAddressViewModel
        }
    }

/** The base URL as a TV would type it: no user info, no query, no fragment. Null if it does not parse. */
internal fun String.shareableAddress(): String? =
    toHttpUrlOrNull()
        ?.newBuilder()
        ?.username("")
        ?.password("")
        ?.query(null)
        ?.fragment(null)
        ?.build()
        ?.toString()
