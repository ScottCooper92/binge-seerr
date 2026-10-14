package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTvSpanishScreenPreviews
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.previewAdminOverview
import com.cooper.binge.companion.seerr.ui.hub.previewReady
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.tv.SampleSettings
import com.cooper.binge.companion.seerr.ui.tv.SampleSettingsAdmin
import com.cooper.binge.companion.seerr.ui.tv.SampleSettingsWithApp
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent

/**
 * The television settings sheet: its list of groups with Disconnect last, then a page for each kind of group: the connection and what
 * can be done to it, the confirm Disconnect asks on its own page, the server's status, the admin's media server with its
 * library scan, this app's own group and the code the bug report shows.
 */
class TvSettingsScreenshotTest {
    /** The sheet's first page: the groups, then Disconnect after them all, in the error colour. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Root() {
        Sheet(SampleSettings)
    }

    /** The connection's page: its read-outs, then Edit connection, the one thing a television can do about it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Connection() {
        Sheet(SampleSettings, page = groupPage(stringResource(R.string.settings_group_connection)))
    }

    /** Disconnect asks first, on a page of its own: what it does, then Disconnect and Cancel. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun DisconnectConfirm() {
        Sheet(SampleSettings, page = PAGE_DISCONNECT)
    }

    /** An admin's media server page: the library scan row, over the note that the rest is read here and changed on the phone. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MediaServer() {
        Sheet(SampleSettingsAdmin, page = groupPage(stringResource(R.string.server_settings_media_server)))
    }

    /** A scan that started: the row says so in place of the server's name, until the note clears (#1053). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScanStarted() {
        Sheet(
            SampleSettingsAdmin,
            page = groupPage(stringResource(R.string.server_settings_media_server)),
            scanEvent = EditorEvent.Notice(R.string.tv_settings_scan_started),
        )
    }

    /** What the home board used to carry at its head: whether the server answers, and an update where there is one. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ServerStatus() {
        Sheet(SampleSettings, page = serverPage(), hub = previewReady(overview = previewAdminOverview()))
    }

    /** The status rows in Spanish: each count carries its own words, in the translator's order (#1042). */
    @PreviewTest
    @SeerrTvSpanishScreenPreviews
    @Composable
    fun ServerStatusSpanish() {
        Sheet(SampleSettings, page = serverPage(), hub = previewReady(overview = previewAdminOverview()))
    }

    /** Binge missing: its row is the one to press, opening its Play Store listing. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ServerStatusBingeMissing() {
        Sheet(
            SampleSettings,
            page = serverPage(),
            hub = previewReady(overview = previewAdminOverview(), bingeStatus = BingeStatus.NotInstalled),
        )
    }

    /** This app's own group: the bug report row and the two switches, each saying where it stands. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportBug() {
        Sheet(SampleSettingsWithApp, page = groupPage(stringResource(R.string.settings_group_app)))
    }

    /** The code the phone scans, on a page of the sheet. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportBugCode() {
        Sheet(SampleSettingsWithApp, page = PAGE_BUG_REPORT)
    }

    /** Before the settings are read (#1053): the sheet's title over its loading line. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        Sheet(SettingsUiState.Loading)
    }

    @Composable
    private fun serverPage() = groupPage(stringResource(R.string.tv_settings_group_server))

    @Composable
    private fun Sheet(
        state: SettingsUiState,
        page: String = PAGE_SETTINGS,
        hub: HubUiState? = null,
        scanEvent: EditorEvent? = null,
    ) {
        TvSettingsSheet(
            state = state,
            actions = TvSettingsActions(),
            onDismissRequest = {},
            hub = hub,
            initialPage = page,
            initialScanEvent = scanEvent,
        )
    }
}
