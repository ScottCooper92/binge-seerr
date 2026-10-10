package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.handoff.AddressCandidate
import io.github.scottcooper92.binge.seerr.handoff.AddressLocality
import io.github.scottcooper92.binge.seerr.handoff.AddressSource
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.SignInForm

private val SHEET_WIDTH = 411.dp

/**
 * The phone's confirmation before it sends a server address to a television (#323): the field on its
 * own, with a not-local note, with the server's other addresses as suggestions, and with an entry that
 * is not an address. The modal
 * window does not capture, so each frame renders the sheet's content on its container colour at a
 * phone's width, as `MediaStatusSheetScreenshotTest` does.
 */
class SendAddressSheetScreenshotTest {
    /** A scanned code asks for the TV's PIN first (#803): two digits typed, the third box next. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun enterPin() = Frame(SendAddressUiState.EnterPin(tv = "192.168.86.53", entered = "48"))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun wrongPin() = Frame(SendAddressUiState.EnterPin(tv = "192.168.86.53", wrong = true))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun ready() = Frame(sampleReady())

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun failed() = Frame(sampleReady().copy(failed = true))

    /** The one-step sign-in (#772): the switch that sends the phone's session with the address, turned on. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun readyWithSignIn() = Frame(sampleReady().copy(signIn = SignInOffer(userName = "Ana"), signInChosen = true))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInWithSession() = Frame(SendAddressUiState.SigningIn("192.168.86.53", SignInStep.Session(awaiting = 1)))

    /** A TV already on its sign-in step: carrying on there is one tap, with the typed fields under it. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInFormWithSession() =
        Frame(
            SendAddressUiState.SigningIn(
                "192.168.86.53",
                SignInStep.Form(
                    "Living room",
                    listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local),
                    SignInForm(mode = SeerrSignInMode.Jellyfin),
                    sessionOffer = SignInOffer(userName = "Ana"),
                ),
            ),
        )

    /** Carrying on from a code this phone did not send: the form names the address the TV is signing in to (#1085). */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInFormCarriedOn() =
        Frame(
            SendAddressUiState.SigningIn(
                "192.168.86.53",
                SignInStep.Form(
                    "Living room",
                    listOf(SeerrSignInMode.Local),
                    SignInForm(mode = SeerrSignInMode.Local),
                    address = "http://192.168.1.66:5055/",
                ),
            ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sent() = Frame(SendAddressUiState.Sent(tv = "192.168.86.53"))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInWaiting() = Frame(SendAddressUiState.SigningIn("192.168.86.53", SignInStep.Waiting))

    /** A public plain-HTTP address: the TV asks its own user, and the sheet says to answer there (#912). */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInConfirmOnTv() = Frame(SendAddressUiState.SigningIn("192.168.86.53", SignInStep.ConfirmOnTv()))

    /** The sign-in the code's key makes possible: the phone's own fields, with the note that says where they go. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInForm() =
        Frame(
            SendAddressUiState.SigningIn(
                "192.168.86.53",
                SignInStep.Form(
                    "Living room",
                    listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local),
                    SignInForm(mode = SeerrSignInMode.Local, email = "ana@example.com", password = "secret"),
                ),
            ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInRejected() =
        Frame(
            SendAddressUiState.SigningIn(
                "192.168.86.53",
                SignInStep.Form(
                    "Living room",
                    listOf(SeerrSignInMode.Jellyfin),
                    SignInForm(mode = SeerrSignInMode.Jellyfin, username = "ana"),
                    rejected = true,
                ),
            ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInConnected() = Frame(SendAddressUiState.SigningIn("192.168.86.53", SignInStep.Connected))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun refused() = Frame(SendAddressUiState.Refused)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun loading() = Frame(SendAddressUiState.Loading)

    /** This phone has no server to send (#1053). */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun notConnected() = Frame(SendAddressUiState.NotConnected)

    /** The TV is signing in on its own: the phone says where, and has nothing more to send. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signingInOnTv() = Frame(SendAddressUiState.SigningIn("192.168.86.53", SignInStep.OnTv(server = "Living room")))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun singleNotLocal() = Frame(ready(candidate("http://100.103.24.117:30042/", AddressSource.Connected, AddressLocality.NotLocal)))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun suggestionsWithServerUrl() =
        Frame(
            ready(
                candidate("http://192.168.86.20:5055/", AddressSource.Remembered, AddressLocality.Local),
                candidate("https://seerr.example.com/", AddressSource.ApplicationUrl, AddressLocality.Unknown),
                candidate("http://100.103.24.117:30042/", AddressSource.Connected, AddressLocality.NotLocal),
            ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun invalidEntry() =
        Frame(
            ready(
                candidate("http://100.103.24.117:30042/", AddressSource.Connected, AddressLocality.NotLocal),
            ).copy(address = "http://:5055"),
        )
}

private fun candidate(
    address: String,
    source: AddressSource,
    locality: AddressLocality,
) = AddressCandidate(address, source, locality)

private fun ready(vararg candidates: AddressCandidate) =
    SendAddressUiState.Ready(
        tv = "192.168.86.53",
        candidates = candidates.toList(),
        address = candidates.first().address,
        isSending = false,
        failed = false,
    )

private fun sampleReady() = ready(candidate("http://seerr.lan:5055/", AddressSource.Connected, AddressLocality.Unknown))

@Composable
private fun Frame(state: SendAddressUiState) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        SendAddressSheetContent(state = state, actions = SendAddressActions())
    }
}
