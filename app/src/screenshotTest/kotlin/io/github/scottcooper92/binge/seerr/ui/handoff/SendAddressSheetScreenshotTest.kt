package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

private val SHEET_WIDTH = 411.dp

/**
 * The phone's confirmation before it sends its server's address to a television (#323). The modal
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
}

private fun sampleReady() =
    SendAddressUiState.Ready(serverAddress = "http://seerr.lan:5055/", tv = "192.168.86.53", isSending = false, failed = false)

@Composable
private fun Frame(state: SendAddressUiState) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        SendAddressSheetContent(state = state, onSend = {}, onClose = {})
    }
}
