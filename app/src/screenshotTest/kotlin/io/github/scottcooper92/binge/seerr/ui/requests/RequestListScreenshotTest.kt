package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

private val ROW_WIDTH = 411.dp

/**
 * The phone request list row (`RequestRow`/`RequestRowMeta` in `RequestList.kt`), which had no frame
 * exercising its chip/badge crowding — the #346 regression risk (#347): a long status label alone, the
 * same label sharing the media-type/year line with a 4K badge, and the optional season-list line.
 */
class RequestListScreenshotTest {
    /** "Partly available" is the longest chip label in the set — the chip alone must not overflow the row. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun longStatus() = Frame(longStatusRow())

    /** The same long label plus a 4K badge and a year crowd one line — what #346 could have broken. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun fourKAndLongStatus() = Frame(fourKLongStatusRow())

    /** A TV request with specific seasons exercises the optional season-list line. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun seasonList() = Frame(seasonListRow())
}

@Composable
private fun Frame(item: RequestItem) {
    Box(Modifier.width(ROW_WIDTH).background(MaterialTheme.colorScheme.background)) {
        RequestRow(item = item, onClick = {}, onActions = {}, now = ROW_NOW_MILLIS)
    }
}
