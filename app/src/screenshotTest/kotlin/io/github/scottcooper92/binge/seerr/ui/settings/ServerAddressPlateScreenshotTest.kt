package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

/**
 * The plate that lets a user read their server's address across the room to a television instead
 * of typing it there from memory (#323) — large monospace, the way a television's own link plate
 * renders a code.
 */
class ServerAddressPlateScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun plate() = ServerAddressPlate(baseUrl = "http://seerr.lan:5055")
}
