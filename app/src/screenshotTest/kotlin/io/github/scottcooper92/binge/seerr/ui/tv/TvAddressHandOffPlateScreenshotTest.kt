package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import io.github.scottcooper92.binge.seerr.ui.SetupActions

private val NoSetupActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {})

/**
 * The address step's second pane when a phone is to send the address (#323): the code a phone scans with the
 * same URL spelled out under it, and the note that says why there is no code at all.
 */
class TvAddressHandOffPlateScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun listening() =
        TvSetupScreen(
            state = setupAddress().copy(handOff = AddressHandOff.Listening(url = "http://192.168.86.53:41234/a/k7m2pqx4")),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun noLocalNetwork() =
        TvSetupScreen(
            state = setupAddress().copy(handOff = AddressHandOff.Unavailable(AddressHandOff.Reason.NoLocalNetwork)),
            actions = NoSetupActions,
        )
}
