package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.PHONE_WIDTH
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13ComponentPreviews
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS

/**
 * The phone request list row (`RequestRow`/`RequestRowMeta` in `RequestList.kt`), which had no frame
 * exercising its chip/badge crowding — the #346 regression risk (#347): a long status label alone, the
 * same label sharing the media-type/year line with a 4K badge, and the optional season-list line.
 */
class RequestListScreenshotTest {
    /** "Partly available", a long chip label — the chip alone must not overflow the row. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun longStatus() = Frame(longStatusRow())

    /** The same long label plus a 4K badge and a year crowd one line — what #346 could have broken. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun fourKAndLongStatus() = Frame(fourKLongStatusRow())

    /** Approved with nothing downloading (#1719): "Approved · not downloading", not "Downloading". */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun approvedNotDownloading() = Frame(notDownloadingRow())

    /** A TV request with specific seasons exercises the optional season-list line. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun seasonList() = Frame(seasonListRow())

    /**
     * A long title, a long status, a year and a season at font scale 1.3: every line of the row at once, in the face and
     * size that clipped the requester line off the card's bottom edge on a phone.
     */
    @PreviewTest
    @SeerrFontScale13ComponentPreviews
    @Composable
    fun longTitleFontScale13() = Frame(longTitleTvRow())
}

@Composable
private fun Frame(item: RequestItem) {
    Box(Modifier.width(PHONE_WIDTH).background(MaterialTheme.colorScheme.background)) {
        RequestRow(item = item, onClick = {}, onManage = {}, now = FIXED_NOW_MILLIS)
    }
}
