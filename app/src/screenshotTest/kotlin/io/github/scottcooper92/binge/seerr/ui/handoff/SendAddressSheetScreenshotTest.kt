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

private val SHEET_WIDTH = 411.dp

/**
 * The phone's confirmation before it sends a server address to a television (#323): one address on
 * its own, a choice with an Application URL or an address that is not local, and a typed one. The modal
 * window does not capture, so each frame renders the sheet's content on its container colour at a
 * phone's width, as `MediaStatusSheetScreenshotTest` does.
 */
class SendAddressSheetScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun ready() = Frame(sampleReady())

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun failed() = Frame(sampleReady().copy(failed = true))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sent() = Frame(SendAddressUiState.Sent(tv = "192.168.86.53"))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun refused() = Frame(SendAddressUiState.Refused)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun singleNotLocal() = Frame(ready(candidate("http://100.101.102.103:5055/", AddressSource.Connected, AddressLocality.NotLocal)))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun choiceWithApplicationUrl() =
        Frame(
            ready(
                candidate("http://192.168.86.20:5055/", AddressSource.ApplicationUrl, AddressLocality.Local),
                candidate("https://seerr.example.com/", AddressSource.Connected, AddressLocality.Unknown),
            ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun choiceWithNotLocal() =
        Frame(
            ready(
                candidate("http://nas:5055/", AddressSource.Remembered, AddressLocality.Local),
                candidate("http://100.101.102.103:5055/", AddressSource.Connected, AddressLocality.NotLocal),
            ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun anotherAddress() =
        Frame(
            ready(candidate("http://100.101.102.103:5055/", AddressSource.Connected, AddressLocality.NotLocal))
                .copy(choice = AddressChoice.Other, otherAddress = "http://192.168.86:5055", otherInvalid = true),
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
        choice = AddressChoice.Candidate(candidates.first().address),
        otherAddress = "http://:5055",
        otherInvalid = false,
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
