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
import io.github.scottcooper92.binge.seerr.handoff.normaliseServerAddress
import io.github.scottcooper92.binge.seerr.handoff.otherAddressPrefill
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which address the user has chosen to send: one of the candidates, or one they type. */
sealed interface AddressChoice {
    data class Candidate(
        val address: String,
    ) : AddressChoice

    data object Other : AddressChoice
}

/** The phone's confirmation before it sends a server address to a television (#323). */
sealed interface SendAddressUiState {
    data object Loading : SendAddressUiState

    /** The link is not one a television on this network wrote, so nothing is sent anywhere. */
    data object Refused : SendAddressUiState

    /** This phone has no server to send. */
    data object NotConnected : SendAddressUiState

    /**
     * The addresses to choose from, best first, and the [choice] made — the best one until the user
     * picks another. [otherAddress] is the "use another address" field, and [otherInvalid] says the
     * last attempt to send it failed the TV's own rules. [tv] is where it goes.
     */
    data class Ready(
        val tv: String,
        val candidates: List<AddressCandidate>,
        val choice: AddressChoice,
        val otherAddress: String,
        val otherInvalid: Boolean,
        val isSending: Boolean,
        val failed: Boolean,
    ) : SendAddressUiState {
        /** One address and nothing typed: shown on its own, with no list to choose from. */
        val isSingle: Boolean get() = candidates.size == 1 && choice !is AddressChoice.Other

        val canSend: Boolean get() = !isSending && (choice !is AddressChoice.Other || otherAddress.isNotBlank())
    }

    data class Sent(
        val tv: String,
    ) : SendAddressUiState
}

/**
 * Reads a hand-off link and offers the addresses this phone knows for its server — the one it is
 * connected on, the server's Application URL, and any the user typed before — and sends the one the
 * user picks, only when they tap Send, to the television that showed the code.
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
                    choice = AddressChoice.Candidate(candidates.first().address),
                    otherAddress = otherAddressPrefill(connected),
                    otherInvalid = false,
                    isSending = false,
                    failed = false,
                )
        }

        fun choose(choice: AddressChoice) = updateReady { if (it.isSending) it else it.copy(choice = choice, failed = false) }

        fun editOther(value: String) = updateReady { it.copy(otherAddress = value, otherInvalid = false, failed = false) }

        /** Sends the chosen address. Nothing reaches the TV any other way: this runs only from the Send button. */
        fun send() {
            val ready = _uiState.value as? SendAddressUiState.Ready ?: return
            val target = target ?: return
            if (!ready.canSend) return
            val address =
                when (val choice = ready.choice) {
                    is AddressChoice.Candidate -> choice.address
                    AddressChoice.Other -> normaliseServerAddress(ready.otherAddress)
                } ?: return _uiState.update { ready.copy(otherInvalid = true) }
            _uiState.value = ready.copy(isSending = true, failed = false)
            viewModelScope.launch(dispatcher) {
                if (sender.send(target, address)) {
                    if (ready.typedOrRemembered(address)) server?.let { memory.remember(it, address) }
                    _uiState.value = SendAddressUiState.Sent(tv = ready.tv)
                } else {
                    _uiState.value = ready.copy(isSending = false, failed = true)
                }
            }
        }

        /** Typed now, or typed before: kept, or moved to the front. The server's own addresses are found again anyway. */
        private fun SendAddressUiState.Ready.typedOrRemembered(address: String): Boolean =
            when (choice) {
                AddressChoice.Other -> candidates.none { it.address == address && it.source != AddressSource.Remembered }
                is AddressChoice.Candidate -> candidates.any { it.address == address && it.source == AddressSource.Remembered }
            }

        private fun updateReady(transform: (SendAddressUiState.Ready) -> SendAddressUiState.Ready) =
            _uiState.update { state -> (state as? SendAddressUiState.Ready)?.let(transform) ?: state }

        @AssistedFactory
        interface Factory {
            fun create(link: String?): SendAddressViewModel
        }
    }

/** Whether the TV may not reach [this]: an IP literal that is not on a home network. */
internal val AddressCandidate.mayBeUnreachable: Boolean get() = locality == AddressLocality.NotLocal
