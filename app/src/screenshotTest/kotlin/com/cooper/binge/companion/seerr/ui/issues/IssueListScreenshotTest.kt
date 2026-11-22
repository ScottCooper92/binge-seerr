package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.PHONE_WIDTH
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScale20ComponentPreviews
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS

/**
 * The phone issue row (`IssueRow` in `IssueList.kt`, on the shared media row), which had no frame exercising
 * its #418 chip crowding — the state chip, issue type, media type and year share one wrapping line, and the
 * affected season or episode follows the title (#419).
 */
class IssueListScreenshotTest {
    /** The longest issue-type label, sharing the line with an episode-scoped affected label and a comment count below. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun crowdedEpisodeAffected() = Frame(crowdedEpisodeIssueRow())

    /** The season-only affected label, with no reporter so the row falls back to the "unknown" requester label. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun crowdedSeasonAffected() = Frame(crowdedSeasonIssueRow())

    /** The episode row with a score and a genre after the year: the whole status line the shared row draws. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun ratedEpisodeAffected() = Frame(ratedEpisodeIssueRow())

    /** The same row at the largest font scale: the byline, the title with its suffix, the status line and the opening line must all hold. */
    @PreviewTest
    @SeerrFontScale20ComponentPreviews
    @Composable
    fun ratedEpisodeAffectedFontScale20() = Frame(ratedEpisodeIssueRow())

    /** The season-scoped row at the largest font scale. */
    @PreviewTest
    @SeerrFontScale20ComponentPreviews
    @Composable
    fun ratedSeasonAffectedFontScale20() = Frame(ratedSeasonIssueRow())
}

@Composable
private fun Frame(item: IssueItem) {
    Box(Modifier.width(PHONE_WIDTH).background(MaterialTheme.colorScheme.background)) {
        IssueRow(item = item, onClick = {}, now = FIXED_NOW_MILLIS)
    }
}
