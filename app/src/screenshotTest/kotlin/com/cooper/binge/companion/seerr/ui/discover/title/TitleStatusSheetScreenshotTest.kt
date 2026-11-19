package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame

/** The sheet behind a primary with no request to manage. Modal windows do not capture, so each frame renders its content on the sheet's colour. */
class TitleStatusSheetScreenshotTest {
    /** In the library, with the media server named: Watch on it. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun watch() = SheetFrame { TitleStatusSheetContent(TitleState.Available, "https://plex.example/watch", "Plex", true, {}) }

    /** In the library with no link to give: the status and a line saying so. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun inLibraryNoLink() = SheetFrame { TitleStatusSheetContent(TitleState.Available, null, "Plex", true, {}) }

    /** Someone else's request, which this viewer cannot open: the status and a line saying so, with nothing to manage. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun otherRequest() = SheetFrame { TitleStatusSheetContent(TitleState.Requested, null, null, true, {}) }

    /** Processing with no request on file (a server sync): the line claims no requester. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun processing() = SheetFrame { TitleStatusSheetContent(TitleState.Processing, null, null, true, {}) }

    /** A show with no season to request yet, for a viewer who may request: the line says there is nothing to request, not that the account can't. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun nothingToRequest() = SheetFrame { TitleStatusSheetContent(TitleState.Request, null, null, true, {}) }

    /** A viewer whose account may not request. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun cannotRequest() = SheetFrame { TitleStatusSheetContent(TitleState.Request, null, null, false, {}) }

    /** Blocklisted, for a viewer who may manage the blocklist: the same lines with the unblock row under them. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun blocklistedCanUnblock() = SheetFrame { TitleStatusSheetContent(TitleState.Blocklisted, null, null, true, {}, onUnblock = {}) }

    /** Blocklisted: the status and why it cannot be requested. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun blocklisted() = SheetFrame { TitleStatusSheetContent(TitleState.Blocklisted, null, null, true, {}) }
}
