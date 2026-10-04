package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff

/**
 * The television's "send the address from your phone" plate (#323): the code a phone scans with the
 * same URL spelled out under it, and the page that says why there is no code at all.
 */
class TvAddressHandOffPlateScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun listening() =
        TvAddressHandOffPlate(
            handOff = AddressHandOff.Listening(url = "http://192.168.86.53:41234/a/Zq3v9KpL2xWm8RtYb4NcHg"),
            onCancel = {},
        )

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun noLocalNetwork() = TvAddressHandOffPlate(handOff = AddressHandOff.Unavailable(AddressHandOff.Reason.NoLocalNetwork), onCancel = {})
}
