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
import io.github.scottcooper92.binge.seerr.handoff.HandOffKey
import io.github.scottcooper92.binge.seerr.handoff.HandOffSignInModes
import io.github.scottcooper92.binge.seerr.handoff.HandOffStatus
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffLinks
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffTarget
import io.github.scottcooper92.binge.seerr.handoff.TvSignInClient
import io.github.scottcooper92.binge.seerr.handoff.addressCandidates
import io.github.scottcooper92.binge.seerr.handoff.normaliseServerAddress
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.attempt
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
import java.security.MessageDigest

/**
 * Reads a hand-off link, fills an editable address field with the best address this phone knows for
 * its server — the one it is connected on, the server's Application URL, or one sent before — offers
 * the others as suggestions, and sends what is in the field, only when the user taps Send, to the
 * television that showed the code.
 *
 * The link is refused unless it names a private IPv4 address on the user's network
 * ([TvHandOffTarget.isOnLan]): a link is just text a web page can write, and this is the check
 * that stops one from pointing the phone anywhere else. What goes is a server address with any user
 * info stripped and, only if the user chose to share it, the Seerr session sealed to that television's key
 * (#772). Never a bare key, cookie or token.
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

        /** The address this phone sent the TV, if it sent one: the TV has to report the same one before anything sealed goes to it (#1029). */
        @Volatile
        private var sent: String? = null

        /** The address the TV says it is signing in to, exactly as it says it: what credentials are sealed for. */
        @Volatile
        private var signingInTo: String? = null

        init {
            // A code with a key asks for the TV's PIN before anything else (#803): it proves the person holding the
            // phone can see the TV whose key it is, which a link from a web page or a message cannot.
            if (target?.key != null) {
                _uiState.value = SendAddressUiState.EnterPin(tv = target.host)
            } else {
                viewModelScope.launch(dispatcher) { load() }
            }
        }

        /**
         * The PIN boxes changed. A fresh digit after a wrong PIN clears the error, and the last digit checks the PIN here on
         * the phone: a listener that checked it would accept whatever an attacker's listener liked. A match goes on to
         * what the code is for; a miss clears the boxes.
         */
        fun enterPin(value: String) {
            val pinning = _uiState.value as? SendAddressUiState.EnterPin ?: return
            val expected = target?.key?.pin() ?: return
            when {
                value.length < expected.length -> _uiState.value = pinning.copy(entered = value, wrong = false)
                !MessageDigest.isEqual(value.toByteArray(), expected.toByteArray()) ->
                    _uiState.value =
                        pinning.copy(entered = "", wrong = true)
                else -> {
                    _uiState.value = SendAddressUiState.Loading
                    viewModelScope.launch(dispatcher) { load() }
                }
            }
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
            return SignInOffer(userName = attempt { connection.authenticatedUser().displayString() }.getOrNull())
        }

        /** This phone's session cookie, when it signed in as a user; an API key is never shared. */
        private suspend fun sharedSession(): String? = (attempt { connection.current() }.getOrNull()?.auth as? SeerrAuth.Session)?.cookie

        fun chooseSignIn(chosen: Boolean) =
            _uiState.updateReady {
                if (it.isSending ||
                    it.signIn == null
                ) {
                    it
                } else {
                    it.copy(signInChosen = chosen)
                }
            }

        /** The field changed, by typing or by a suggestion; the note under it follows. */
        fun editAddress(value: String) = _uiState.updateReady { if (it.isSending) it else it.copy(address = value, failed = false) }

        /** Sends what is in the field. Nothing reaches the TV any other way: this runs only from the Send button. */
        fun send() {
            val ready = _uiState.value as? SendAddressUiState.Ready ?: return
            val target = target ?: return
            if (!ready.canSend) return
            val address = ready.normalised ?: return
            _uiState.value = ready.copy(isSending = true, failed = false)
            viewModelScope.launch(dispatcher) {
                // One step (#772): with the switch on, the session goes sealed in the same post as the address.
                val sealed =
                    if (ready.signIn != null && ready.signInChosen) sharedSession()?.let { target.sealSession(it, address) } else null
                if (sender.send(target, address, sealed)) {
                    sent = address
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
            _uiState.updateForm { if (it.isSending) it else it.copy(form = it.form.transform(), rejected = false) }

        /** Seals what the form holds for this TV and sends it. The only place credentials leave the phone, and only on the tap. */
        fun sendSignIn() {
            val target = target ?: return
            val state = _uiState.value as? SendAddressUiState.SigningIn ?: return
            val step = state.step as? SignInStep.Form ?: return
            if (!step.canSend) return
            val address = signingInTo ?: return
            val credentials = step.form.toCredentials()
            _uiState.updateForm { it.copy(isSending = true, rejected = false, awaiting = null) }
            viewModelScope.launch(dispatcher) {
                // Not taken at all is its own failure; taken and refused arrives later, in the TV's status.
                val attempt = tv.send(target, address, credentials)
                if (attempt == null) {
                    _uiState.updateForm { it.copy(isSending = false, rejected = true) }
                } else {
                    _uiState.updateForm { it.copy(awaiting = attempt) }
                }
            }
        }

        /** Seals this phone's session for the TV on its sign-in step and sends it: the one-tap way to carry on there. */
        fun sendSession() {
            val target = target ?: return
            val state = _uiState.value as? SendAddressUiState.SigningIn ?: return
            val step = state.step as? SignInStep.Form ?: return
            if (step.isSending || step.sessionOffer == null) return
            val address = signingInTo ?: return
            _uiState.updateForm { it.copy(isSending = true, sendingSession = true, rejected = false, awaiting = null) }
            viewModelScope.launch(dispatcher) {
                val attempt =
                    sharedSession()?.let {
                        tv.send(
                            target,
                            address,
                            HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = it),
                        )
                    }
                if (attempt == null) {
                    _uiState.updateForm { it.copy(isSending = false, sendingSession = false, rejected = true, sessionOffer = null) }
                } else {
                    connection.markSessionShared()
                    _uiState.updateForm { it.copy(awaiting = attempt) }
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
                        if (silent >= LOST_AFTER_SILENT_POLLS) return@launch _uiState.showStep(SignInStep.Lost)
                        if (status != null && !apply(status)) return@launch
                        delay(POLL_MILLIS)
                    }
                }
        }

        /** Applies [status]; false once there is nothing more to follow. */
        private fun apply(status: HandOffStatus): Boolean {
            when (status.state) {
                HandOffStatus.CONNECTED -> _uiState.showStep(SignInStep.Connected)
                HandOffStatus.FAILED -> {
                    // The address named no Seerr server the TV could reach: back to the field, to try another.
                    lastReady?.let { _uiState.value = it.copy(failed = true) }
                    return false
                }
                HandOffStatus.SIGN_IN -> {
                    // A TV signing in somewhere this phone did not send it, or not saying where, gets nothing more (#1029).
                    val address = status.address?.takeIf { sent == null || it.sameServerAs(sent) }
                    if (address == null) {
                        _uiState.showStep(SignInStep.Redirected)
                        return false
                    }
                    signingInTo = address
                    showSignIn(status, address)
                }
                // Nothing to do here: the answer is the TV's, and the sheet follows whatever it is (#912).
                HandOffStatus.CONFIRM ->
                    _uiState.update { state ->
                        val signingIn = state as? SendAddressUiState.SigningIn ?: return@update state
                        when (val step = signingIn.step) {
                            is SignInStep.ConfirmOnTv -> state
                            else -> signingIn.copy(step = SignInStep.ConfirmOnTv(resume = step as? SignInStep.Session))
                        }
                    }
                // Waiting or checking: the TV is on its address step. A sheet on its sign-in step follows it back there, or a
                // send would be refused for a step the TV has left (#804). A session that went with the address is the TV
                // still working on that address, so it stays, and comes back once the TV's user has answered a confirm.
                else ->
                    when (val step = (_uiState.value as? SendAddressUiState.SigningIn)?.step) {
                        is SignInStep.Session -> Unit
                        is SignInStep.ConfirmOnTv -> _uiState.showStep(step.resume ?: SignInStep.Waiting)
                        else -> _uiState.showStep(SignInStep.Waiting)
                    }
            }
            return status.state != HandOffStatus.CONNECTED
        }

        /** Shows the TV's sign-in step, for a TV signing in to [address]. */
        private fun showSignIn(
            status: HandOffStatus,
            address: String,
        ) {
            val server = status.server.orEmpty()
            // Only modes with fields to fill: the TV does not offer the others, and a listener that did would not be believed.
            val modes =
                status.modes
                    .mapNotNull { name -> SeerrSignInMode.entries.firstOrNull { it.name == name } }
                    .filter { it in HandOffSignInModes }
            val step = (_uiState.value as? SendAddressUiState.SigningIn)?.step
            // A session held through the TV's confirm is still the session in flight: the TV may answer it before a poll sees it.
            val held = (step as? SignInStep.ConfirmOnTv)?.resume
            val current = step as? SignInStep.Session ?: held
            val turnedDown = current != null && status.failed && status.attempt >= current.awaiting
            // The TV turned the session down: what's left is typing, or finishing on the TV where it has no fields.
            // A phone that sent the address has checked the TV's against it; one carrying on shows it instead (#1085).
            val shown = address.takeIf { sent == null }
            if (turnedDown) return _uiState.showStep(fallbackFrom(server, modes, shown))
            if (step is SignInStep.Session) return
            if (modes.isEmpty()) return _uiState.showStep(SignInStep.OnTv(server))
            // One update, so the form it keeps is the latest one: a send finishing on another thread is not overwritten.
            _uiState.update { state ->
                val signingIn = state as? SendAddressUiState.SigningIn ?: return@update state
                val current = signingIn.step as? SignInStep.Form
                signingIn.copy(
                    step =
                        when {
                            current == null ->
                                SignInStep.Form(
                                    server,
                                    modes,
                                    SignInForm(mode = modes.first()),
                                    sessionOffer = sessionOfferFor(address),
                                    address = shown,
                                )
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

        /**
         * This phone's session goes only to a TV signing in where this phone sent it, or, for a TV that had its address
         * already, to this phone's own server (#1029). Anywhere else the session is no use, and would reach whoever runs it.
         */
        private fun sessionOfferFor(address: String): SignInOffer? = offer?.takeIf { sent != null || address.sameServerAs(server) }

        private companion object {
            /** States a TV can be in that mean the address is done with: a scan then carries on rather than starting over. */
            val CARRY_ON_STATES = setOf(HandOffStatus.CHECKING, HandOffStatus.CONFIRM, HandOffStatus.SIGN_IN, HandOffStatus.CONNECTED)
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
    address: String?,
): SignInStep =
    if (modes.isEmpty()) {
        SignInStep.OnTv(server)
    } else {
        SignInStep.Form(server, modes, SignInForm(mode = modes.first()), rejected = true, address = address)
    }

/** [session], sealed for this TV's code and [address] as credentials of the session mode; null for a target without a key. */
private fun TvHandOffTarget.sealSession(
    session: String,
    address: String,
): String? =
    key?.seal(
        Json.encodeToString(HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = session)).toByteArray(Charsets.UTF_8),
        context = HandOffKey.context(token, address),
    )

/** Whether two addresses name the same server, however each was written. */
private fun String.sameServerAs(other: String?): Boolean = other != null && normaliseServerAddress(this) == normaliseServerAddress(other)

/** Anything but the server's own addresses, which are found again without being kept. */
private fun SendAddressUiState.Ready.isWorthRemembering(address: String): Boolean =
    candidates.none { it.address == address && it.source != AddressSource.Remembered }

/** Moves a sign-in in progress to [step]; any other state is left as it is. */
private fun MutableStateFlow<SendAddressUiState>.showStep(step: SignInStep) =
    update { (it as? SendAddressUiState.SigningIn)?.copy(step = step) ?: it }

/** Changes the sign-in form, while there is one on screen. */
private fun MutableStateFlow<SendAddressUiState>.updateForm(transform: (SignInStep.Form) -> SignInStep.Form) =
    update { state ->
        val signingIn = state as? SendAddressUiState.SigningIn ?: return@update state
        val form = signingIn.step as? SignInStep.Form ?: return@update state
        signingIn.copy(step = transform(form))
    }

/** Changes the address field, while it is on screen. */
private fun MutableStateFlow<SendAddressUiState>.updateReady(transform: (SendAddressUiState.Ready) -> SendAddressUiState.Ready) =
    update { state -> (state as? SendAddressUiState.Ready)?.let(transform) ?: state }
