package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

/**
 * Fixed and kept far past the 30-day relative-date window `formatRelativeOrAbsolute` switches on
 * (mirrors `RequestListPreviewData.kt`'s `ROW_NOW_MILLIS`/`REQUESTED_AT_MILLIS`), so the row's
 * reported-at date always renders the same absolute date, whenever the suite runs.
 */
internal const val ISSUE_ROW_NOW_MILLIS = 1_770_000_000_000L
private const val REPORTED_AT_MILLIS = 1_759_000_000_000L
private const val TV_TMDB_ID = 95_396

/**
 * #418's inline chip row at its most crowded: the longest issue-type label sharing the line with the
 * state chip and media-type tag, an episode-scoped affected label, and a comment count on the meta
 * line below (#419).
 */
internal fun crowdedEpisodeIssueRow(): IssueItem =
    IssueItem(
        id = 1,
        tmdbId = TV_TMDB_ID,
        mediaType = RequestMediaType.Tv,
        title = "The Marvelous Mrs. Maisel",
        posterUrl = null,
        year = "2017",
        type = IssueType.Subtitles,
        status = IssueStatus.Resolved,
        reportedBy = "Alexandria",
        reportedById = 7,
        commentCount = 12,
        createdAtMillis = REPORTED_AT_MILLIS,
        updatedAtMillis = REPORTED_AT_MILLIS,
        problem = "Subtitles drift out of sync starting around the midpoint of the episode and get progressively worse.",
        problemSeason = 12,
        problemEpisode = 34,
    )

/** The season-only branch of the affected label, with no reporter to fall back to the "unknown" label. */
internal fun crowdedSeasonIssueRow(): IssueItem =
    crowdedEpisodeIssueRow().copy(
        id = 2,
        type = IssueType.Audio,
        status = IssueStatus.Open,
        reportedBy = null,
        reportedById = null,
        commentCount = 0,
        problemEpisode = null,
    )
