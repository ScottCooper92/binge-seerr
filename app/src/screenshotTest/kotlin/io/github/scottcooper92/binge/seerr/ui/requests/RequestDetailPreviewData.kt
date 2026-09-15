package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode

private const val REQUESTED_AT_MILLIS = 1_759_000_000_000L
private const val UPDATED_AT_MILLIS = 1_759_500_000_000L
private const val PLAYS = 12
private const val PLAYS_7 = 3
private const val PLAYS_30 = 8
private const val TMDB_ID = 1396
private const val MEDIA_ID = 900
private const val REQUEST_ID = 11
private const val EPISODE_COUNT = 13

private const val OVERVIEW =
    "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing to secure his " +
        "family's future. What starts as a short, careful arrangement becomes something neither he nor " +
        "the people around him can step back from, and the distance between the man who began it and " +
        "the one still doing it stops being something anyone can measure."

/** A request in the state the page's primary reads as a review: pending, and this viewer moderates. */
internal fun pendingDetail(): RequestDetail =
    detail(
        actions = RequestActions(canApprove = true, canDecline = true, canRemove = true, canBlock = true),
        status = SeerrRequestStatusCode.Pending,
        mediaStatus = SeerrMediaStatusCode.Pending,
        canEdit = true,
    )

/** The same request once it is settled: nothing to approve, and a media record to manage. */
internal fun settledDetail(): RequestDetail =
    detail(
        actions = RequestActions(canRemove = true, canBlock = true),
        status = SeerrRequestStatusCode.Approved,
        mediaStatus = SeerrMediaStatusCode.Available,
        canEdit = false,
    )

private fun detail(
    actions: RequestActions,
    status: SeerrRequestStatusCode,
    mediaStatus: SeerrMediaStatusCode,
    canEdit: Boolean,
): RequestDetail =
    RequestDetail(
        item =
            RequestItem(
                id = REQUEST_ID,
                tmdbId = TMDB_ID,
                mediaType = RequestMediaType.Tv,
                title = "Breaking Bad",
                posterUrl = null,
                year = "2008",
                requestedBy = "Ada",
                requestedById = 7,
                requestedAtMillis = REQUESTED_AT_MILLIS,
                status = status,
                mediaStatus = mediaStatus,
                download = null,
                seasonNumbers = listOf(1),
                is4k = false,
            ),
        actions = actions,
        canEdit = canEdit,
        canEditDestination = false,
        backdropUrl = null,
        overview = OVERVIEW,
        modifiedBy = "Grace",
        updatedAtMillis = UPDATED_AT_MILLIS,
        seasons = listOf(SeasonState(number = 1, name = null, episodeCount = EPISODE_COUNT, status = mediaStatus)),
        destination = RequestDestination(serverName = "Sonarr", profileName = "HD-1080p", rootFolder = "/tv", tags = listOf("kids")),
        downloads = emptyList(),
        mediaId = MEDIA_ID,
        canReportIssue = true,
        webUrl = "https://seerr.example/tv/1396",
        mediaServerUrl = "https://jellyfin.example/web/#/details?id=a1",
        serviceUrl = "https://sonarr.example/series/breaking-bad",
        media =
            MediaRecord(
                mediaId = MEDIA_ID,
                isTv = true,
                instances =
                    listOf(
                        MediaInstance(
                            is4k = false,
                            status = mediaStatus,
                            serviceUrl = "https://sonarr.example/series/breaking-bad",
                            mediaServerUrl = "https://jellyfin.example/web/#/details?id=a1",
                            watch = WatchStats(PLAYS, PLAYS_7, PLAYS_30, listOf("Ada", "Grace")),
                        ),
                    ),
                canSetStatus = true,
                canClearData = true,
                canDeleteFiles = true,
            ),
    )
