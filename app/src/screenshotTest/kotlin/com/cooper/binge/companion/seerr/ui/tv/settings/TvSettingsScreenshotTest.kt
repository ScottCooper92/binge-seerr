package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTvSpanishScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.previewAdminOverview
import com.cooper.binge.companion.seerr.ui.hub.previewReady
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.tv.ConfirmDialogFrame
import com.cooper.binge.companion.seerr.ui.tv.SampleSettings
import com.cooper.binge.companion.seerr.ui.tv.SampleSettingsAdmin
import com.cooper.binge.companion.seerr.ui.tv.SampleSettingsWithApp
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent

/**
 * The television settings board: the connection/server list, the disconnect confirmation focused,
 * and — admin only — the media server row's library scan option. Mirrors the states already sketched
 * in `TvBoardPreviewData.kt`.
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

    /** Disconnect asks first: what it does, then Disconnect and Cancel, in a dialog. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun DisconnectConfirm() {
        ConfirmDialogFrame(
            title = stringResource(R.string.hub_disconnect_confirm_title),
            message = stringResource(R.string.hub_disconnect_confirm_message),
            confirmLabel = stringResource(R.string.tv_settings_disconnect_confirm),
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

    /** A scan that started: the media server row says so, and keeps saying it until the next scan (#1510). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScanStarted() {
        TvSettingsBoard(
            state = SampleSettingsAdmin,
            onEditConnection = {},
            onDisconnect = {},
            initialFocusedKey = KEY_MEDIA_SERVER,
            initialFocusedOptionLabel = "Start library scan",
            initialScanEvent = EditorEvent.Notice(R.string.tv_settings_scan_started),
        )
    }

    /** A scan the server refused: the row is marked, says why, and its option is Retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScanFailed() {
        TvSettingsBoard(
            state = SampleSettingsAdmin,
            onEditConnection = {},
            onDisconnect = {},
            initialFocusedKey = KEY_MEDIA_SERVER,
            initialFocusedOptionLabel = "Retry",
            initialScanEvent = EditorEvent.Failed(SeerrError.Unreachable),
        )
    }

    /** What the home board used to carry at its head: whether the server answers, and an update where there is one. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ServerStatus() {
        TvSettingsBoard(
            state = SampleSettings,
            onEditConnection = {},
            onDisconnect = {},
            hub = previewReady(overview = previewAdminOverview()),
            initialFocusedKey = KEY_ACTIVITY,
        )
    }

    /** The status rows in Spanish: each count carries its own words, in the translator's order (#1042). */
    @PreviewTest
    @SeerrTvSpanishScreenPreviews
    @Composable
    fun ServerStatusSpanish() {
        TvSettingsBoard(
            state = SampleSettings,
            onEditConnection = {},
            onDisconnect = {},
            hub = previewReady(overview = previewAdminOverview()),
            initialFocusedKey = KEY_ACTIVITY,
        )
    }

    /** Binge missing: the row carries the one option, opening its Play Store listing. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ServerStatusBingeMissing() {
        TvSettingsBoard(
            state = SampleSettings,
            onEditConnection = {},
            onDisconnect = {},
            hub = previewReady(overview = previewAdminOverview(), bingeStatus = BingeStatus.NotInstalled),
            initialFocusedKey = KEY_BINGE,
            initialFocusedOptionLabel = "Open the Play Store",
        )
    }

    /** This app's own group: the bug report row, with its one option to show a code. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportBug() {
        TvSettingsBoard(
            state = SampleSettingsWithApp,
            onEditConnection = {},
            onDisconnect = {},
            initialFocusedKey = KEY_REPORT_BUG,
            initialFocusedOptionLabel = "Show code",
        )
    }

    /** The code the phone scans, over the board. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportBugCode() {
        TvSettingsBoard(
            state = SampleSettingsWithApp,
            onEditConnection = {},
            onDisconnect = {},
            initialFocusedKey = KEY_REPORT_BUG,
            initialShowingBugReport = true,
        )
    }

    /** The usage data switch, off: its one option turns it on. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ShareUsageData() {
        TvSettingsBoard(
            state = SampleSettingsWithApp,
            onEditConnection = {},
            onDisconnect = {},
            initialFocusedKey = KEY_SHARE_USAGE_DATA,
            initialFocusedOptionLabel = "Turn on",
        )
    }

    /** Before the settings are read (#1053): the board's title over its loading plate. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        TvSettingsBoard(state = SettingsUiState.Loading, onEditConnection = {}, onDisconnect = {})
    }
}
