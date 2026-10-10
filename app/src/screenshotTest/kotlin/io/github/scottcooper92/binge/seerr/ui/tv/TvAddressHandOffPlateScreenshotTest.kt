package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import io.github.scottcooper92.binge.seerr.ui.HAND_OFF_CODE
import io.github.scottcooper92.binge.seerr.ui.NoSetupActions

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
}
