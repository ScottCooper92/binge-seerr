package com.cooper.binge.companion.seerr.ui.handoff

import com.cooper.binge.companion.seerr.handoff.HAND_OFF_SESSION_MODE
import com.cooper.binge.companion.seerr.handoff.HandOffCredentials
import com.cooper.binge.companion.seerr.handoff.HandOffKey
import com.cooper.binge.companion.seerr.handoff.HandOffStatus
import com.cooper.binge.companion.seerr.handoff.TvHandOffTarget
import com.cooper.binge.companion.seerr.handoff.normaliseServerAddress
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.ui.SignInForm
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/*
 * The sign-in steps [SendAddressViewModel] moves the sheet through, as pure functions of what the TV last said:
 * which form to show, when the TV has refused what this phone sent, where a refused session falls back to, and the
 * session sealed for the TV.
 */

/**
 * The form for the TV's sign-in step, from the one on screen ([this], null if none): a fresh one, this one marked
 * refused once the TV has turned down what it sent, or this one as it is.
 */
internal fun SignInStep.Form?.followedTo(
    status: HandOffStatus,
    server: String,
    modes: List<SeerrSignInMode>,
    sessionOffer: SignInOffer?,
    address: String?,
): SignInStep.Form =
    when {
        this == null -> SignInStep.Form(server, modes, SignInForm(mode = modes.first()), sessionOffer = sessionOffer, address = address)
        refusedBy(status) ->
            copy(
                isSending = false,
                rejected = true,
                awaiting = null,
                sendingSession = false,
                // A session the TV turned down won't do better a second time.
                sessionOffer = this.sessionOffer.takeUnless { sendingSession },
            )
        else -> this
    }

/** What a form holds, as the TV's own sign-in reads it. */
internal fun SignInForm.toCredentials(): HandOffCredentials =
    HandOffCredentials(mode = mode.name, apiKey = apiKey, email = email, username = username, password = password)

/** A `failed` counts only once the TV has reached the attempt this phone sent; before that it is the last attempt's. */
internal fun SignInStep.Form.refusedBy(status: HandOffStatus): Boolean =
    isSending && status.failed && awaiting?.let { status.attempt >= it } == true

/** Where the sheet goes when the TV turns the session down: typing, or finishing on the TV where it has no fields. */
internal fun fallbackFrom(
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
internal fun TvHandOffTarget.sealSession(
    session: String,
    address: String,
): String? =
    key?.seal(
        Json.encodeToString(HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = session)).toByteArray(Charsets.UTF_8),
        context = HandOffKey.context(token, address),
    )

/**
 * This phone's session goes only to a TV signing in where this phone sent it ([sentByThisPhone]), or, for a TV that had its
 * address already, to this phone's own server (#1029). Anywhere else the session is no use, and would reach whoever runs it.
 *
 * A server's id is its own word for itself, which anyone who can reach it can read and another server can repeat, so it never
 * decides alone. Ids that differ withhold the session whatever the addresses say. The same address needs no more. The same id
 * at another address (a home network against a VPN) is offered only with the user's confirm of the TV's address, and sealed
 * after it. Without an id on both sides, the addresses decide.
 */
internal fun sessionOfferFor(
    offer: SignInOffer?,
    sentByThisPhone: Boolean,
    address: String,
    tvServerId: String?,
    server: String?,
    serverId: String?,
): SignInOffer? {
    if (offer == null) return null
    if (sentByThisPhone) return offer
    val theirs = tvServerId?.takeIf { it.isNotBlank() }
    if (theirs != null && serverId != null && theirs != serverId) return null
    if (server != null && normaliseServerAddress(address) == normaliseServerAddress(server)) return offer
    return offer.takeIf { theirs != null && theirs == serverId }?.copy(confirmAddress = address)
}
