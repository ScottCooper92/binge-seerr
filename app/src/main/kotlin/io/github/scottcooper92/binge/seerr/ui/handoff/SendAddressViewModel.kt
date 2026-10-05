package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.handoff.AddressCandidate
import io.github.scottcooper92.binge.seerr.handoff.AddressLocality
import io.github.scottcooper92.binge.seerr.handoff.AddressSender
import io.github.scottcooper92.binge.seerr.handoff.AddressSource
import io.github.scottcooper92.binge.seerr.handoff.ApplicationUrlReader
import io.github.scottcooper92.binge.seerr.handoff.HandOffAddressMemory
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffLinks
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffTarget
import io.github.scottcooper92.binge.seerr.handoff.addressCandidates
import io.github.scottcooper92.binge.seerr.handoff.addressLocality
import io.github.scottcooper92.binge.seerr.handoff.normaliseServerAddress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The phone's confirmation before it sends a server address to a television (#323). */
sealed interface SendAddressUiState {
    data object Loading : SendAddressUiState

    /** The link is not one a television on this network wrote, so nothing is sent anywhere. */
    data object Refused : SendAddressUiState

    /** This phone has no server to send. */
    data object NotConnected : SendAddressUiState

    /**
     * The address field, filled with the best of [candidates] until the user edits it, and [tv], where
     * it goes. Everything else is read from [address] as it stands, so the note under the field always
     * describes what Send would send.
     */
    data class Ready(
        val tv: String,
        val candidates: List<AddressCandidate>,
        val address: String,
        val isSending: Boolean,
        val failed: Boolean,
    ) : SendAddressUiState {
        /** The field as it would be sent, or null when the TV's own form would refuse it. */
        val normalised: String? get() = normaliseServerAddress(address)

        /** Whether the field holds something to complain about: an entry that is not an address. Blank is not an error, only unsendable. */
        val isInvalid: Boolean get() = address.isNotBlank() && normalised == null

        val isNotLocal: Boolean get() = normalised?.let(::addressLocality) == AddressLocality.NotLocal

        val canSend: Boolean get() = !isSending && normalised != null

        /** The other addresses to offer as one-tap fills: none when there is only one, and never the one already in the field. */
        val suggestions: List<AddressCandidate>
            get() = if (candidates.size < 2) emptyList() else candidates.filter { it.address != normalised }
    }

    data class Sent(
        val tv: String,
    ) : SendAddressUiState
}

/**
 * Reads a hand-off link, fills an editable address field with the best address this phone knows for
 * its server — the one it is connected on, the server's Application URL, or one sent before — offers
 * the others as suggestions, and sends what is in the field, only when the user taps Send, to the
 * television that showed the code.
 *
 * The link is refused unless it names a private IPv4 address on the user's network
 * ([TvHandOffTarget.isOnLan]): a link is just text a web page can write, and this is the check
 * that stops one from pointing the phone anywhere else. What goes is a server address with any user
 * info stripped, never a key, cookie or token of the Seerr session.
 */
@HiltViewModel(assistedFactory = SendAddressViewModel.Factory::class)
class SendAddressViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val sender: AddressSender,
        private val applicationUrl: ApplicationUrlReader,
        private val memory: HandOffAddressMemory,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted link: String?,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SendAddressUiState>(SendAddressUiState.Loading)
        val uiState: StateFlow<SendAddressUiState> = _uiState.asStateFlow()

        private val target: TvHandOffTarget? = TvHandOffLinks.parse(link)?.takeIf { it.isOnLan }

        /** The connected server's own address, which is also what the remembered addresses are filed under. */
        private var server: String? = null

        init {
            viewModelScope.launch(dispatcher) { load() }
        }

        private suspend fun load() {
            val target = target ?: return _uiState.update { SendAddressUiState.Refused }
            val connected =
                connection.credentials
                    .first()
                    ?.baseUrl
                    ?.let(::normaliseServerAddress)
                    ?: return _uiState.update { SendAddressUiState.NotConnected }
            server = connected
            val candidates = addressCandidates(connected, applicationUrl.read(), memory.remembered(connected))
            _uiState.value =
                SendAddressUiState.Ready(
                    tv = target.host,
                    candidates = candidates,
                    address = candidates.first().address,
                    isSending = false,
                    failed = false,
                )
        }

        /** The field changed, by typing or by a suggestion; the note under it follows. */
        fun editAddress(value: String) = updateReady { if (it.isSending) it else it.copy(address = value, failed = false) }

        /** Sends what is in the field. Nothing reaches the TV any other way: this runs only from the Send button. */
        fun send() {
            val ready = _uiState.value as? SendAddressUiState.Ready ?: return
            val target = target ?: return
            if (!ready.canSend) return
            val address = ready.normalised ?: return
            _uiState.value = ready.copy(isSending = true, failed = false)
            viewModelScope.launch(dispatcher) {
                if (sender.send(target, address)) {
                    if (ready.isWorthRemembering(address)) server?.let { memory.remember(it, address) }
                    _uiState.value = SendAddressUiState.Sent(tv = ready.tv)
                } else {
                    _uiState.value = ready.copy(isSending = false, failed = true)
                }
            }
        }

        /** Anything but the server's own addresses, which are found again without being kept. */
        private fun SendAddressUiState.Ready.isWorthRemembering(address: String): Boolean =
            candidates.none { it.address == address && it.source != AddressSource.Remembered }

        private fun updateReady(transform: (SendAddressUiState.Ready) -> SendAddressUiState.Ready) =
            _uiState.update { state -> (state as? SendAddressUiState.Ready)?.let(transform) ?: state }

        @AssistedFactory
        interface Factory {
            fun create(link: String?): SendAddressViewModel
        }
    }
