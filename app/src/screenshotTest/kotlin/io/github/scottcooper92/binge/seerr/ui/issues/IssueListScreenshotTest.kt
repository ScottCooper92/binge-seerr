package io.github.scottcooper92.binge.seerr.ui.issues

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
 * The phone issue row (`IssueRow`/`IssueRowMeta` in `IssueList.kt`), which had no frame exercising
 * its #418 chip crowding — the state chip, media-type tag, issue-type tag and optional affected-episode
 * label all share one line with no wrapping (#419).
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
}

@Composable
private fun Frame(item: IssueItem) {
    Box(Modifier.width(ROW_WIDTH).background(MaterialTheme.colorScheme.background)) {
        IssueRow(item = item, onClick = {}, now = ISSUE_ROW_NOW_MILLIS)
    }
}
