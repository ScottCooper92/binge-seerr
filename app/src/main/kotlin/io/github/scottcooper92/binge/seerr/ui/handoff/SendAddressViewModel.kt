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
import io.github.scottcooper92.binge.seerr.handoff.AddressSource
import io.github.scottcooper92.binge.seerr.handoff.ApplicationUrlReader
import io.github.scottcooper92.binge.seerr.handoff.HAND_OFF_SESSION_MODE
import io.github.scottcooper92.binge.seerr.handoff.HandOffAddressMemory
import io.github.scottcooper92.binge.seerr.handoff.HandOffCredentials
import io.github.scottcooper92.binge.seerr.handoff.HandOffSignInModes
import io.github.scottcooper92.binge.seerr.handoff.HandOffStatus
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffLinks
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffTarget
import io.github.scottcooper92.binge.seerr.handoff.TvSignInClient
import io.github.scottcooper92.binge.seerr.handoff.addressCandidates
import io.github.scottcooper92.binge.seerr.handoff.normaliseServerAddress
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.displayString
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        @Assisted private val scanned: Boolean = false,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SendAddressUiState>(SendAddressUiState.Loading)
        val uiState: StateFlow<SendAddressUiState> = _uiState.asStateFlow()

        private val target: TvHandOffTarget? = TvHandOffLinks.parse(link)?.takeIf { it.isOnLan }

        /** The address field as it stood when it was sent, for the sheet to return to if the TV could not use the address. */
        private var lastReady: SendAddressUiState.Ready? = null

        private var followJob: Job? = null

        /** Signing the TV in as this phone's user, when that's possible: read once, offered on the address and the form. */
        private var offer: SignInOffer? = null

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
            // Only for a code this app scanned: a link a web page can fire carries a key of its own choosing, and the
            // session must not go to whoever wrote it.
            offer = if (connected != null && scanned) signInOffer(target) else null
            // A TV past its address (a phone carrying on where it left off, or a code from the sign-in step) needs no
            // address from this one: go straight to where the TV is.
            if (target.key != null) {
                val status = tv.status(target)
                if (status != null && status.state in CARRY_ON_STATES) {
                    _uiState.value = SendAddressUiState.SigningIn(tv = target.host, step = SignInStep.Waiting)
                    if (apply(status)) follow(target)
                    return
                }
            }
            val candidates = connected?.let { addressCandidates(it, applicationUrl.read(), memory.remembered(it)) }.orEmpty()
            _uiState.value =
                SendAddressUiState.Ready(
                    tv = target.host,
                    candidates = candidates,
                    address = candidates.firstOrNull()?.address.orEmpty(),
                    isSending = false,
                    failed = false,
                    signIn = offer,
                )
        }

        /** An offer only with a key to seal with and a user's session to share; the name is best-effort. */
        private suspend fun signInOffer(target: TvHandOffTarget): SignInOffer? {
            if (target.key == null || sharedSession() == null) return null
            return SignInOffer(userName = runCatching { connection.authenticatedUser().displayString() }.getOrNull())
        }

        /** This phone's session cookie, when it signed in as a user; an API key is never shared. */
        private suspend fun sharedSession(): String? =
            (runCatching { connection.current() }.getOrNull()?.auth as? SeerrAuth.Session)?.cookie

        fun chooseSignIn(chosen: Boolean) = updateReady { if (it.isSending || it.signIn == null) it else it.copy(signInChosen = chosen) }

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
                // One step (#772): with the switch on, the session goes sealed in the same post as the address.
                val sealed = if (ready.signIn != null && ready.signInChosen) sharedSession()?.let { target.sealSession(it) } else null
                if (sender.send(target, address, sealed)) {
                    if (sealed != null) connection.markSessionShared()
                    if (ready.isWorthRemembering(address)) server?.let { memory.remember(it, address) }
                    if (target.key != null) {
                        lastReady = ready.copy(isSending = false, failed = false)
                        // The TV's first attempt is the session, if one went: its `failed` for attempt 1 means turned down.
                        val step = if (sealed != null) SignInStep.Session(awaiting = 1) else SignInStep.Waiting
                        _uiState.value = SendAddressUiState.SigningIn(tv = ready.tv, step = step)
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

        /** Seals this phone's session for the TV on its sign-in step and sends it: the one-tap way to carry on there. */
        fun sendSession() {
            val target = target ?: return
            val state = _uiState.value as? SendAddressUiState.SigningIn ?: return
            val step = state.step as? SignInStep.Form ?: return
            if (step.isSending || step.sessionOffer == null) return
            updateForm { it.copy(isSending = true, sendingSession = true, rejected = false, awaiting = null) }
            viewModelScope.launch(dispatcher) {
                val attempt = sharedSession()?.let { tv.send(target, HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = it)) }
                if (attempt == null) {
                    updateForm { it.copy(isSending = false, sendingSession = false, rejected = true, sessionOffer = null) }
                } else {
                    connection.markSessionShared()
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
                // Waiting or checking: the TV is on its address step. A sheet on its sign-in step follows it back there, or a
                // send would be refused for a step the TV has left (#804). A session that went with the address is the TV
                // still working on that address, so it stays.
                else -> if ((_uiState.value as? SendAddressUiState.SigningIn)?.step !is SignInStep.Session) showStep(SignInStep.Waiting)
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
            val current = (_uiState.value as? SendAddressUiState.SigningIn)?.step
            if (current is SignInStep.Session) {
                // The TV turned the session down: what's left is typing, or finishing on the TV where it has no fields.
                if (status.failed && status.attempt >= current.awaiting) showStep(fallbackFrom(server, modes))
                return
            }
            if (modes.isEmpty()) return showStep(SignInStep.OnTv(server))
            // One update, so the form it keeps is the latest one: a send finishing on another thread is not overwritten.
            _uiState.update { state ->
                val signingIn = state as? SendAddressUiState.SigningIn ?: return@update state
                val current = signingIn.step as? SignInStep.Form
                signingIn.copy(
                    step =
                        when {
                            current == null -> SignInStep.Form(server, modes, SignInForm(mode = modes.first()), sessionOffer = offer)
                            current.refusedBy(status) ->
                                current.copy(
                                    isSending = false,
                                    rejected = true,
                                    awaiting = null,
                                    sendingSession = false,
                                    // A session the TV turned down won't do better a second time.
                                    sessionOffer = current.sessionOffer.takeUnless { current.sendingSession },
                                )
                            else -> current
                        },
                )
            }
        }

        private fun showStep(step: SignInStep) = _uiState.update { (it as? SendAddressUiState.SigningIn)?.copy(step = step) ?: it }

        private fun updateForm(transform: (SignInStep.Form) -> SignInStep.Form) =
            _uiState.update { state ->
                val signingIn = state as? SendAddressUiState.SigningIn ?: return@update state
                val form = signingIn.step as? SignInStep.Form ?: return@update state
                signingIn.copy(step = transform(form))
            }

        private fun updateReady(transform: (SendAddressUiState.Ready) -> SendAddressUiState.Ready) =
            _uiState.update { state -> (state as? SendAddressUiState.Ready)?.let(transform) ?: state }

        private companion object {
            /** States a TV can be in that mean the address is done with: a scan then carries on rather than starting over. */
            val CARRY_ON_STATES = setOf(HandOffStatus.CHECKING, HandOffStatus.SIGN_IN, HandOffStatus.CONNECTED)
            const val POLL_MILLIS = 1_500L
            const val LOST_AFTER_SILENT_POLLS = 4
        }

        @AssistedFactory
        interface Factory {
            fun create(
                link: String?,
                scanned: Boolean,
            ): SendAddressViewModel
        }
    }

/** What a form holds, as the TV's own sign-in reads it. */
internal fun SignInForm.toCredentials(): HandOffCredentials =
    HandOffCredentials(mode = mode.name, apiKey = apiKey, email = email, username = username, password = password)

/** A `failed` counts only once the TV has reached the attempt this phone sent; before that it is the last attempt's. */
private fun SignInStep.Form.refusedBy(status: HandOffStatus): Boolean =
    isSending && status.failed && awaiting?.let { status.attempt >= it } == true

/** Where the sheet goes when the TV turns the session down: typing, or finishing on the TV where it has no fields. */
private fun fallbackFrom(
    server: String,
    modes: List<SeerrSignInMode>,
): SignInStep =
    if (modes.isEmpty()) {
        SignInStep.OnTv(server)
    } else {
        SignInStep.Form(server, modes, SignInForm(mode = modes.first()), rejected = true)
    }

/** [session], sealed for this TV's code as credentials of the session mode; null for a target without a key. */
private fun TvHandOffTarget.sealSession(session: String): String? =
    key?.seal(
        Json.encodeToString(HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = session)).toByteArray(Charsets.UTF_8),
        context = token,
    )

/** Anything but the server's own addresses, which are found again without being kept. */
private fun SendAddressUiState.Ready.isWorthRemembering(address: String): Boolean =
    candidates.none { it.address == address && it.source != AddressSource.Remembered }
