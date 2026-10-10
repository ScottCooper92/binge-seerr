package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import io.github.scottcooper92.binge.seerr.ui.HAND_OFF_CODE
import io.github.scottcooper92.binge.seerr.ui.NoSetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupError

/**
 * Setup on a television as one screen (#772): the code for a phone, with the line under it following the TV from
 * waiting through the server it found, and the note that says why there is no code at all.
 */
class TvAddressHandOffPlateScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun listening() =
        TvSetupScreen(
            state = setupAddress().copy(handOff = HAND_OFF_CODE, code = HAND_OFF_CODE),
            actions = NoSetupActions,
            offerHandOff = true,
        )

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun checking() =
        TvSetupScreen(state = setupAddress().copy(code = HAND_OFF_CODE, isInspecting = true), actions = NoSetupActions, offerHandOff = true)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun foundTheServer() = TvSetupScreen(state = setupSignIn().copy(code = HAND_OFF_CODE), actions = NoSetupActions, offerHandOff = true)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun noLocalNetwork() =
        TvSetupScreen(
            state = setupAddress().copy(handOff = AddressHandOff.Unavailable(AddressHandOff.Reason.NoLocalNetwork)),
            actions = NoSetupActions,
            offerHandOff = true,
        )

    /** Before the TV has a code to show (#1053): the line says it is getting one ready. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun preparing() = TvSetupScreen(state = setupAddress(), actions = NoSetupActions, offerHandOff = true)

    /** The address the phone sent did not answer: the line under the code is the error. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun addressFailed() =
        TvSetupScreen(
            state = setupAddress(error = SetupError.Unreachable).copy(code = HAND_OFF_CODE),
            actions = NoSetupActions,
            offerHandOff = true,
        )

    /** A server the phone cannot sign the TV in to (Plex, Quick Connect): the line says to finish on the TV. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun foundOnTv() =
        TvSetupScreen(
            state =
                setupSignIn(server = SampleSetupServer.copy(modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect)))
                    .copy(code = HAND_OFF_CODE),
            actions = NoSetupActions,
            offerHandOff = true,
        )

    /** The phone's sign-in is on its way to the server. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun signingIn() =
        TvSetupScreen(state = setupSignIn(isConnecting = true).copy(code = HAND_OFF_CODE), actions = NoSetupActions, offerHandOff = true)

    /** The server refused the phone's sign-in: the line under the code is the error. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun signInFailed() =
        TvSetupScreen(
            state = setupSignIn(error = SetupError.Rejected).copy(code = HAND_OFF_CODE),
            actions = NoSetupActions,
            offerHandOff = true,
        )

    /** The TV could not open its listener: no code, and the note says why. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun couldNotListen() =
        TvSetupScreen(
            state = setupAddress().copy(handOff = AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen)),
            actions = NoSetupActions,
            offerHandOff = true,
        )
}
