package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.ui.users.settings.MediaServerLinkContent

private val PLEX_LINK = LinkFlow.Plex(code = "A1B2", authUrl = "https://app.plex.tv/auth", launchPending = false)
private val QUICK_CONNECT = LinkFlow.QuickConnect(code = "482913")

/**
 * The sheets that link an account: the code to approve elsewhere, and the media server's own credentials. Modal windows do not
 * capture, so each frame renders the stateless body at a phone's width, and again in the side sheet at the expanded width.
 */
class LinkSheetsScreenshotTest {
    /** Plex: the code, a wait, the button that reopens its page, and Cancel. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun plexCode() = SheetFrame { SetupLinkContent(link = PLEX_LINK, onOpenPlex = {}, onCancel = {}) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun plexCodeExpanded() = SideSheetFrame { SetupLinkContent(link = PLEX_LINK, onOpenPlex = {}, onCancel = {}) }

    /** Quick Connect has no page to reopen, so the button is not offered. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun quickConnectCode() = SheetFrame { SetupLinkContent(link = QUICK_CONNECT, onOpenPlex = {}, onCancel = {}) }

    /** The media server's credentials, with Quick Connect offered above them. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun mediaServerWithQuickConnect() =
        SheetFrame {
            MediaServerLinkContent(origin = UserOrigin.Jellyfin, canQuickConnect = true, onQuickConnect = {}, onLink = {
                _,
                _,
                ->
            })
        }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun mediaServerExpanded() =
        SideSheetFrame {
            MediaServerLinkContent(origin = UserOrigin.Jellyfin, canQuickConnect = true, onQuickConnect = {}, onLink = { _, _ -> })
        }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun mediaServerCredentialsOnly() =
        SheetFrame { MediaServerLinkContent(origin = UserOrigin.Emby, canQuickConnect = false, onQuickConnect = {}, onLink = { _, _ -> }) }
}
