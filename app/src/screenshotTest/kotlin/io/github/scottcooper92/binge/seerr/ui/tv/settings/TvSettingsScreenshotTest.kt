package io.github.scottcooper92.binge.seerr.ui.tv.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvPreviews
import io.github.scottcooper92.binge.seerr.ui.tv.SampleSettings

/**
 * The television settings board: the connection/server list and the disconnect confirmation focused.
 * Mirrors the states already sketched in `TvBoardPreviews.kt`.
 */
class TvSettingsScreenshotTest {
    @PreviewTest
    @SeerrTvPreviews
    @Composable
    fun Board() {
        TvSettingsBoard(state = SampleSettings, onEditConnection = {}, onDisconnect = {}, initialListHasFocus = true)
    }

    @PreviewTest
    @SeerrTvPreviews
    @Composable
    fun Disconnect() {
        TvSettingsBoard(
            state = SampleSettings,
            onEditConnection = {},
            onDisconnect = {},
            initialFocusedKey = KEY_DISCONNECT,
            initialFocusedOptionLabel = "Disconnect",
        )
    }
}
