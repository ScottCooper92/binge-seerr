package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13ComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame

/** The sheet behind a primary with no request to manage. Modal windows do not capture, so each frame renders its content on the sheet's colour. */
class TitleStatusSheetScreenshotTest {
    /** Available with 4K still open: Request in 4K. Watch is the page's primary. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun watchAndRequestIn4k() =
        SheetFrame {
            TitleStatusSheetContent(TitleState.Available, true, fourKOpen = true)
        }

    /** Partly available with seasons left: the remaining seasons, then Request in 4K. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun partlyAvailableAllRows() =
        SheetFrame {
            TitleStatusSheetContent(
                TitleState.PartiallyAvailable,
                true,
                fourKOpen = true,
                remainingOpen = true,
            )
        }

    /** The same rows in Spanish, whose labels run longer. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun partlyAvailableAllRowsSpanish() =
        SheetFrame {
            TitleStatusSheetContent(
                TitleState.PartiallyAvailable,
                true,
                fourKOpen = true,
                remainingOpen = true,
            )
        }

    /** The same rows at 1.3x font. */
    @PreviewTest
    @SeerrFontScale13ComponentPreviews
    @Composable
    fun partlyAvailableAllRowsLargeFonts() =
        SheetFrame {
            TitleStatusSheetContent(
                TitleState.PartiallyAvailable,
                true,
                fourKOpen = true,
                remainingOpen = true,
            )
        }

    /** In the library with nothing more to request: the status and a line saying so. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun inLibraryNoLink() = SheetFrame { TitleStatusSheetContent(TitleState.Available, true) }

    /** Someone else's request, which this viewer cannot open: the status and a line saying so, with nothing to manage. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun otherRequest() = SheetFrame { TitleStatusSheetContent(TitleState.Requested, true) }

    /** Processing with no request on file (a server sync): the line claims no requester. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun processing() = SheetFrame { TitleStatusSheetContent(TitleState.Processing, true) }

    /** A show with no season to request yet, for a viewer who may request: the line says there is nothing to request, not that the account can't. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun nothingToRequest() = SheetFrame { TitleStatusSheetContent(TitleState.Request, true) }

    /** Every request was declined and this viewer may not ask again: the status and a line saying so. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun declined() = SheetFrame { TitleStatusSheetContent(TitleState.Declined, false) }

    /** The same in Spanish. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun declinedSpanish() = SheetFrame { TitleStatusSheetContent(TitleState.Declined, false) }

    /** The same at font scale 1.3: the line wraps and nothing is cut off. */
    @PreviewTest
    @SeerrFontScale13ComponentPreviews
    @Composable
    fun declinedLargeFonts() = SheetFrame { TitleStatusSheetContent(TitleState.Declined, false) }

    /** A viewer whose account may not request. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun cannotRequest() = SheetFrame { TitleStatusSheetContent(TitleState.Request, false) }

    /** Blocklisted, for a viewer who may manage the blocklist: the same lines with the unblock row under them. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun blocklistedCanUnblock() = SheetFrame { TitleStatusSheetContent(TitleState.Blocklisted, true, onUnblock = {}) }

    /** Blocklisted: the status and why it cannot be requested. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun blocklisted() = SheetFrame { TitleStatusSheetContent(TitleState.Blocklisted, true) }
}
