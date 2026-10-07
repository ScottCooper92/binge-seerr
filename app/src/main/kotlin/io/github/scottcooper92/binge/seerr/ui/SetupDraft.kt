package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.seerr.LocalNetworkPermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.insecurePublicHostOrNull
import io.github.scottcooper92.binge.seerr.seerr.isBlockedByLocalNetwork
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Everything [SetupViewModel] holds between the user's steps, from which [toUiState] builds what the screen shows. */
internal data class SetupDraft(
    val serverUrl: String = "",
    val server: SetupServer? = null,
    val form: SignInForm = SignInForm(),
    val busy: Boolean = false,
    val link: LinkFlow? = null,
    val error: SetupError? = null,
    val notice: SetupNotice? = null,
    /** The credentials being edited, which the form must not read as "connected". */
    val editing: SeerrCredentials? = null,
    /** The public host the user opted in to reach over plain HTTP, if any. */
    val cleartextHost: String? = null,
    /** The television's hand-off from a phone, while its plate is up. */
    val handOff: AddressHandOff? = null,
    /** Whether [serverUrl] is the address a phone sent, untouched since. */
    val received: Boolean = false,
    /** The hand-off's live code, kept through the sign-in step so a phone can carry on there. */
    val code: AddressHandOff.Listening? = null,
    /** The session a phone sent with [serverUrl], until the server it names has been read and has judged it. */
    val handedSession: String? = null,
    /** How many sets of credentials a phone has sent this TV, taken or not; the phone's way to tell which attempt an error is about. */
    val attempts: Int = 0,
    /** Bumped when the local-network permission may have changed, so the state is built again from the live answer. */
    val permissionReads: Int = 0,
)

/** What the screen shows for this draft, with [saved] the connection already stored. */
internal fun SetupDraft.toUiState(
    saved: SeerrCredentials?,
    localNetwork: LocalNetworkPermission,
): SetupUiState {
    val server = server
    return when {
        // While editing, the connection being edited is not "connected": only new credentials are.
        saved != null && saved != editing -> SetupUiState.Connected(saved)
        server == null -> {
            val insecureHost = serverUrl.insecurePublicHostOrNull()
            SetupUiState.Address(
                serverUrl = serverUrl,
                insecure = insecureHost != null,
                cleartextAllowed = insecureHost != null && insecureHost == cleartextHost,
                isInspecting = busy,
                error = error,
                handOff = handOff,
                needsLocalNetwork = serverUrl.isBlockedByLocalNetwork(localNetwork),
                code = code,
            )
        }
        else ->
            SetupUiState.SignIn(
                server = server,
                form = form,
                isConnecting = busy,
                link = link,
                error = error,
                notice = notice,
                code = code,
            )
    }
}

/** Every attempt ends here: the secret leaves the form once it is stored encrypted, or the failure is shown. */
internal fun MutableStateFlow<SetupDraft>.finish(
    failure: Throwable?,
    analytics: Analytics,
) {
    if (failure is CancellationException) return
    analytics.event(
        AnalyticsEvents.SIGN_IN,
        mapOf(AnalyticsEvents.PARAM_METHOD to value.form.mode.name, AnalyticsEvents.PARAM_SUCCESS to (failure == null)),
    )
    update { current ->
        if (failure == null) {
            current.copy(busy = false, link = null, editing = null, form = SignInForm(mode = current.form.mode))
        } else {
            current.copy(busy = false, link = null, error = failure.toSetupError())
        }
    }
}
