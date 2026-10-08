package io.github.scottcooper92.binge.seerr.ui.handoff

import io.github.scottcooper92.binge.seerr.handoff.AddressCandidate
import io.github.scottcooper92.binge.seerr.handoff.AddressLocality
import io.github.scottcooper92.binge.seerr.handoff.addressLocality
import io.github.scottcooper92.binge.seerr.handoff.normaliseServerAddress
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.SignInForm

/** The phone's confirmation before it sends a server address to a television (#323). */
sealed interface SendAddressUiState {
    data object Loading : SendAddressUiState

    /** The link is not one a television on this network wrote, so nothing is sent anywhere. */
    data object Refused : SendAddressUiState

    /** This phone has no server to send. */
    data object NotConnected : SendAddressUiState

    /**
     * A code with a key, before anything else: the PIN the TV shows beside it (#803). [entered] is what the boxes hold,
     * and [wrong] that the last complete PIN didn't match.
     */
    data class EnterPin(
        val tv: String,
        val entered: String = "",
        val wrong: Boolean = false,
    ) : SendAddressUiState

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
        /** The offer to sign the TV in as this phone's user (#772): null with no key to seal it or no user session to share. */
        val signIn: SignInOffer? = null,
        /** The user's switch for [signIn]. Off every time the sheet opens; nothing remembers it. */
        val signInChosen: Boolean = false,
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

/** Who the TV would be signed in as: [userName] is null when the server didn't say, and the copy says "you" instead. */
data class SignInOffer(
    val userName: String?,
)

/** Where the TV is, as the phone's sign-in sheet shows it. */
sealed interface SignInStep {
    /** The TV has the address and is asking that server who it is. */
    data object Waiting : SignInStep

    /**
     * The address is plain HTTP over the internet, and the TV is asking its user whether to connect anyway. [resume] is
     * the session that went with the address, which the TV signs in with once it is answered.
     */
    data class ConfirmOnTv(
        val resume: Session? = null,
    ) : SignInStep

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
        /** One-tap sign-in as this phone's user (#772), until the TV has turned that session down. */
        val sessionOffer: SignInOffer? = null,
        /** Whether the attempt in flight is the session rather than the typed form. */
        val sendingSession: Boolean = false,
    ) : SignInStep {
        val canSend: Boolean get() = !isSending && form.canSubmit
    }

    /** The phone's own session went with the address, and the TV is signing in with it; [awaiting] is the attempt it is. */
    data class Session(
        val awaiting: Int,
    ) : SignInStep

    data object Connected : SignInStep

    /** The TV stopped answering: it was switched off, left the page, or is no longer on this network. */
    data object Lost : SignInStep
}
