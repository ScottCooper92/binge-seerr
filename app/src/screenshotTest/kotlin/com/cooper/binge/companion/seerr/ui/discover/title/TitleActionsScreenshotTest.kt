package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.preview.PHONE_WIDTH
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13ComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews

/**
 * The row of actions under a title's stats, for none, one and two round buttons at 1x, 1.3x and 2x. The primary shares a row with
 * none or one, and takes a row of its own, the round buttons centred below it, from two; the worst case is the longest state's
 * label at the largest font.
 */
class TitleActionsScreenshotTest {
    /** No trailer and nothing to report: the primary alone, across the row. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun noRoundButtons() = Frame(trailer = false, report = false)

    /** Only a trailer: it shares the row with the primary. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun oneRoundButton() = Frame(trailer = true, report = false)

    /** Trailer and Report: the primary on its own row, the two buttons centred below. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun twoRoundButtons() = Frame(trailer = true, report = true)

    /** One round button at font scale 1.3. */
    @PreviewTest
    @SeerrFontScale13ComponentPreviews
    @Composable
    fun oneRoundButtonLargeText() = Frame(trailer = true, report = false)

    /** Two round buttons at font scale 1.3. */
    @PreviewTest
    @SeerrFontScale13ComponentPreviews
    @Composable
    fun twoRoundButtonsLargeText() = Frame(trailer = true, report = true)

    /** One round button at font scale 2: the primary keeps its label whole beside it. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun oneRoundButtonLargestText() = Frame(trailer = true, report = false)

    /** Two round buttons at font scale 2: the primary's label stays on one line across its own row. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun twoRoundButtonsLargestText() = Frame(trailer = true, report = true)
}

@Composable
private fun Frame(
    trailer: Boolean,
    report: Boolean,
) {
    Box(Modifier.width(PHONE_WIDTH).background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
        TitleActions(
            model =
                titleActionsModel(
                    VersionState(TitleAvailability.PartiallyAvailable, mayRequest = false),
                    null,
                    emptyList(),
                    mayReport = report,
                ),
            onPrimary = {},
            onTrailer = if (trailer) ({}) else null,
            onReport = if (report) ({}) else null,
        )
    }
}
