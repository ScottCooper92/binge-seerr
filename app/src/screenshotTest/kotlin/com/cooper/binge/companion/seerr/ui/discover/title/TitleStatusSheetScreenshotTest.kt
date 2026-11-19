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
    fun watch() = SheetFrame { TitleStatusSheetContent(TitleState.Available, "https://plex.example/watch", "Plex", {}) }

    /** In the library with no link to give: the status and a line saying so. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun inLibraryNoLink() = SheetFrame { TitleStatusSheetContent(TitleState.Available, null, "Plex", {}) }

    /** Someone else's request, which this viewer cannot open: the status and a line saying so, with nothing to manage. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun otherRequest() = SheetFrame { TitleStatusSheetContent(TitleState.Processing, null, null, {}) }

    /** Blocklisted: the status and why it cannot be requested. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun blocklisted() = SheetFrame { TitleStatusSheetContent(TitleState.Blocklisted, null, null, {}) }
}
