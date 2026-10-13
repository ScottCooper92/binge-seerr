package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistItem
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestDownload
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.users.UserOrigin

/*
 * The app's own rows at their worst: every optional line filled and long text, for the no-clipping test to render at
 * font scales 1.3 and 2.0. A row that is fine on a short title at the default size is the usual way a clip ships.
 */

internal const val LONG_TITLE = "The Lord of the Rings: The Rings of Power, Extended Edition with Every Commentary Track"
internal const val LONG_NAME = "Alexandria Fitzgerald-Montgomery"
private const val LONG_TEXT =
    "The subtitles drift out of sync starting around the midpoint of the episode and get progressively worse until the end"
private const val LONG_AGO_MILLIS = 1_759_000_000_000L

/** A request with a wrapping title, a long status, 4K, a year, seasons, a requester and a running download. */
internal fun worstCaseRequest(): RequestItem =
    RequestItem(
        id = 1,
        tmdbId = 95_396,
        mediaType = RequestMediaType.Tv,
        title = LONG_TITLE,
        posterUrl = null,
        year = "2022",
        requestedBy = LONG_NAME,
        requestedById = 4,
        requestedAtMillis = LONG_AGO_MILLIS,
        status = SeerrRequestStatusCode.Approved,
        mediaStatus = SeerrMediaStatusCode.PartiallyAvailable,
        download = RequestDownload(fraction = 0.6f, etaMinutes = 95, downloading = true),
        seasonNumbers = listOf(1, 2, 4, 5, 6),
        is4k = true,
    )

/** An issue with a wrapping title, a long problem line, a season and episode, a long reporter and a comment count. */
internal fun worstCaseIssue(): IssueItem =
    IssueItem(
        id = 1,
        tmdbId = 95_396,
        mediaType = RequestMediaType.Tv,
        title = LONG_TITLE,
        posterUrl = null,
        year = "2022",
        type = IssueType.Subtitles,
        status = IssueStatus.Open,
        reportedBy = LONG_NAME,
        reportedById = 4,
        commentCount = 128,
        createdAtMillis = LONG_AGO_MILLIS,
        updatedAtMillis = LONG_AGO_MILLIS,
        problem = LONG_TEXT,
        problemSeason = 12,
        problemEpisode = 24,
    )

/** A blocklist entry with a wrapping title, a long blocker and enough tags to wrap onto several lines. */
internal fun worstCaseBlocklistItem(): BlocklistItem =
    BlocklistItem(
        id = 1,
        tmdbId = 95_396,
        mediaType = RequestMediaType.Tv,
        title = LONG_TITLE,
        posterUrl = null,
        year = "2017",
        addedBy = LONG_NAME,
        addedAtMillis = LONG_AGO_MILLIS,
        tags = listOf("Not available", "Adult content", "Anime", "Sports", "4K discovery", "Documentary"),
    )

/** A user with a long name, a long email and handle, an admin's permissions and a large request count. */
internal fun worstCaseUser(): UserItem =
    UserItem(
        id = 7,
        name = LONG_NAME,
        email = "alexandria.fitzgerald-montgomery@a-rather-long-domain-name.example.com",
        handle = "alexandria.fitzgerald-montgomery",
        avatarUrl = null,
        origin = UserOrigin.Plex,
        permissions = 2,
        requestCount = 12_840,
        createdAtMillis = LONG_AGO_MILLIS,
    )
