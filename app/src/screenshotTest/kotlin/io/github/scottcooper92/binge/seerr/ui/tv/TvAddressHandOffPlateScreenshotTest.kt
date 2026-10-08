package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import io.github.scottcooper92.binge.seerr.ui.SetupActions

private val NoSetupActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {})

private val CODE = AddressHandOff.Listening(url = "http://192.168.86.53:41234/a/k7m2pqx4", pin = "4821")

/**
 * Setup on a television as one screen (#772): the code for a phone, with the line under it following the TV from
 * waiting through the server it found, and the note that says why there is no code at all.
 */
class TvAddressHandOffPlateScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun listening() = TvSetupScreen(state = setupAddress().copy(handOff = CODE, code = CODE), actions = NoSetupActions, offerHandOff = true)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun checking() =
        TvSetupScreen(state = setupAddress().copy(code = CODE, isInspecting = true), actions = NoSetupActions, offerHandOff = true)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun foundTheServer() = TvSetupScreen(state = setupSignIn().copy(code = CODE), actions = NoSetupActions, offerHandOff = true)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun noLocalNetwork() =
        TvSetupScreen(
            state = setupAddress().copy(handOff = AddressHandOff.Unavailable(AddressHandOff.Reason.NoLocalNetwork)),
            actions = NoSetupActions,
            offerHandOff = true,
        )
}
