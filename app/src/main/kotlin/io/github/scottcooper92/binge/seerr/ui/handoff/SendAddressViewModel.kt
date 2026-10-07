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
import io.github.scottcooper92.binge.seerr.handoff.HandOffCredentials
import io.github.scottcooper92.binge.seerr.handoff.HandOffSignInModes
import io.github.scottcooper92.binge.seerr.handoff.HandOffStatus
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffLinks
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffTarget
import io.github.scottcooper92.binge.seerr.handoff.TvSignInClient
import io.github.scottcooper92.binge.seerr.handoff.addressCandidates
import io.github.scottcooper92.binge.seerr.handoff.addressLocality
import io.github.scottcooper92.binge.seerr.handoff.normaliseServerAddress
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.SignInForm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /**
     * After the address, when the code was scanned and so carries a key: the TV's progress, and on its sign-in step
     * a form whose credentials are sealed for that TV alone.
     */
    data class SigningIn(
        val tv: String,
        val step: SignInStep,
    ) : SendAddressUiState
}

/** Where the TV is, as the phone's sign-in sheet shows it. */
sealed interface SignInStep {
    /** The TV has the address and is asking that server who it is. */
    data object Waiting : SignInStep

    /** The TV reached [server] but offers only sign-ins that finish with a code on the TV, so there is nothing to type here. */
    data class OnTv(
        val server: String,
    ) : SignInStep

    /**
     * The TV is on its sign-in step for [server], offering [modes]. [isSending] runs from the tap until the TV has
     * said how the attempt went; [rejected] is that it said no. [awaiting] is the number the TV counted the attempt
     * as, once it has taken it: the TV's `failed` is about this attempt only when its own count has reached it.
     */
    data class Form(
        val server: String,
        val modes: List<SeerrSignInMode>,
        val form: SignInForm,
        val isSending: Boolean = false,
        val rejected: Boolean = false,
        val awaiting: Int? = null,
    ) : SignInStep {
        val canSend: Boolean get() = !isSending && form.canSubmit
    }

    data object Connected : SignInStep

    /** The TV stopped answering: it was switched off, left the page, or is no longer on this network. */
    data object Lost : SignInStep
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
        private val tv: TvSignInClient,
        private val applicationUrl: ApplicationUrlReader,
        private val memory: HandOffAddressMemory,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted link: String?,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SendAddressUiState>(SendAddressUiState.Loading)
        val uiState: StateFlow<SendAddressUiState> = _uiState.asStateFlow()

        private val target: TvHandOffTarget? = TvHandOffLinks.parse(link)?.takeIf { it.isOnLan }

        /** The address field as it stood when it was sent, for the sheet to return to if the TV could not use the address. */
        private var lastReady: SendAddressUiState.Ready? = null

        private var followJob: Job? = null

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
            // A phone with no server can still type one when the code carried a key: the sign-in that follows
            // is the point, so it does not need this phone to be signed in anywhere.
            if (connected == null && target.key == null) return _uiState.update { SendAddressUiState.NotConnected }
            server = connected
            val candidates = connected?.let { addressCandidates(it, applicationUrl.read(), memory.remembered(it)) }.orEmpty()
            _uiState.value =
                SendAddressUiState.Ready(
                    tv = target.host,
                    candidates = candidates,
                    address = candidates.firstOrNull()?.address.orEmpty(),
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
                    if (target.key != null) {
                        lastReady = ready.copy(isSending = false, failed = false)
                        _uiState.value = SendAddressUiState.SigningIn(tv = ready.tv, step = SignInStep.Waiting)
                        follow(target)
                    } else {
                        _uiState.value = SendAddressUiState.Sent(tv = ready.tv)
                    }
                } else {
                    _uiState.value = ready.copy(isSending = false, failed = true)
                }
            }
        }

        /** The sign-in form changed; nothing leaves the phone until [sendSignIn]. */
        fun editSignIn(transform: SignInForm.() -> SignInForm) =
            updateForm { if (it.isSending) it else it.copy(form = it.form.transform(), rejected = false) }

        /** Seals what the form holds for this TV and sends it. The only place credentials leave the phone, and only on the tap. */
        fun sendSignIn() {
            val target = target ?: return
            val state = _uiState.value as? SendAddressUiState.SigningIn ?: return
            val step = state.step as? SignInStep.Form ?: return
            if (!step.canSend) return
            val credentials = step.form.toCredentials()
            updateForm { it.copy(isSending = true, rejected = false, awaiting = null) }
            viewModelScope.launch(dispatcher) {
                // Not taken at all is its own failure; taken and refused arrives later, in the TV's status.
                val attempt = tv.send(target, credentials)
                if (attempt == null) {
                    updateForm { it.copy(isSending = false, rejected = true) }
                } else {
                    updateForm { it.copy(awaiting = attempt) }
                }
            }
        }

        /** Reads the TV's progress until it is done or gone, moving the sheet with it. */
        private fun follow(target: TvHandOffTarget) {
            followJob?.cancel()
            followJob =
                viewModelScope.launch(dispatcher) {
                    var silent = 0
                    while (true) {
                        val status = tv.status(target)
                        silent = if (status == null) silent + 1 else 0
                        if (silent >= LOST_AFTER_SILENT_POLLS) return@launch showStep(SignInStep.Lost)
                        if (status != null && !apply(status)) return@launch
                        delay(POLL_MILLIS)
                    }
                }
        }

        /** Applies [status]; false once there is nothing more to follow. */
        private fun apply(status: HandOffStatus): Boolean {
            when (status.state) {
                HandOffStatus.CONNECTED -> showStep(SignInStep.Connected)
                HandOffStatus.FAILED -> {
                    // The address named no Seerr server the TV could reach: back to the field, to try another.
                    lastReady?.let { _uiState.value = it.copy(failed = true) }
                    return false
                }
                HandOffStatus.SIGN_IN -> showSignIn(status)
                else ->
                    if (_uiState.value.let {
                            it is SendAddressUiState.SigningIn && it.step !is SignInStep.Waiting
                        }
                    ) {
                        Unit
                    } else {
                        showStep(SignInStep.Waiting)
                    }
            }
            return status.state != HandOffStatus.CONNECTED
        }

        private fun showSignIn(status: HandOffStatus) {
            val server = status.server.orEmpty()
            // Only modes with fields to fill: the TV does not offer the others, and a listener that did would not be believed.
            val modes =
                status.modes
                    .mapNotNull { name -> SeerrSignInMode.entries.firstOrNull { it.name == name } }
                    .filter { it in HandOffSignInModes }
            if (modes.isEmpty()) return showStep(SignInStep.OnTv(server))
            val current = (_uiState.value as? SendAddressUiState.SigningIn)?.step as? SignInStep.Form
            showStep(
                when {
                    current == null -> SignInStep.Form(server, modes, SignInForm(mode = modes.first()))
                    current.refusedBy(status) -> current.copy(isSending = false, rejected = true, awaiting = null)
                    else -> current
                },
            )
        }

        /** A `failed` counts only once the TV has reached the attempt this phone sent; before that it is the last attempt's. */
        private fun SignInStep.Form.refusedBy(status: HandOffStatus): Boolean =
            isSending && status.failed && awaiting?.let { status.attempt >= it } == true

        private fun showStep(step: SignInStep) = _uiState.update { (it as? SendAddressUiState.SigningIn)?.copy(step = step) ?: it }

        private fun updateForm(transform: (SignInStep.Form) -> SignInStep.Form) =
            _uiState.update { state ->
                val signingIn = state as? SendAddressUiState.SigningIn ?: return@update state
                val form = signingIn.step as? SignInStep.Form ?: return@update state
                signingIn.copy(step = transform(form))
            }

        /** Anything but the server's own addresses, which are found again without being kept. */
        private fun SendAddressUiState.Ready.isWorthRemembering(address: String): Boolean =
            candidates.none { it.address == address && it.source != AddressSource.Remembered }

        private fun updateReady(transform: (SendAddressUiState.Ready) -> SendAddressUiState.Ready) =
            _uiState.update { state -> (state as? SendAddressUiState.Ready)?.let(transform) ?: state }

        private companion object {
            const val POLL_MILLIS = 1_500L
            const val LOST_AFTER_SILENT_POLLS = 4
        }

        @AssistedFactory
        interface Factory {
            fun create(link: String?): SendAddressViewModel
        }
    }

/** What a form holds, as the TV's own sign-in reads it. */
internal fun SignInForm.toCredentials(): HandOffCredentials =
    HandOffCredentials(mode = mode.name, apiKey = apiKey, email = email, username = username, password = password)
