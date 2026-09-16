package io.github.scottcooper92.binge.seerr.ui.tv.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.tv.SampleSettings
import io.github.scottcooper92.binge.seerr.ui.tv.SampleSettingsAdmin

/**
 * The television settings board: the connection/server list, the disconnect confirmation focused,
 * and — admin only — the media server row's library scan option. Mirrors the states already sketched
 * in `TvBoardPreviews.kt`.
 */
class TvSettingsScreenshotTest {
    /** A non-admin sees the connection group only: no media server row, so no scan option either. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Board() {
        TvSettingsBoard(state = SampleSettings, onEditConnection = {}, onDisconnect = {}, initialListHasFocus = true)
    }

    @PreviewTest
    @SeerrTvScreenPreviews
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

    /** An admin's board: the media server row now carries "Start library scan". */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MediaServer() {
        TvSettingsBoard(
            state = SampleSettingsAdmin,
            onEditConnection = {},
            onDisconnect = {},
            initialFocusedKey = KEY_MEDIA_SERVER,
            initialFocusedOptionLabel = "Start library scan",
        )
    }
}
