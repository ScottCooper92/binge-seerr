package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode

private const val REQUESTED_AT_MILLIS = 1_759_000_000_000L
private const val UPDATED_AT_MILLIS = 1_759_500_000_000L
private const val SIBLING_DECLINED_AT_MILLIS = 1_744_000_000_000L
private const val SIBLING_COMPLETED_AT_MILLIS = 1_749_000_000_000L
private const val PLAYS = 12
private const val PLAYS_7 = 3
private const val PLAYS_30 = 8
private const val TMDB_ID = 1396
private const val MEDIA_ID = 900
private const val REQUEST_ID = 11
private const val SIBLING_REQUEST_ID = 12
private const val OTHER_SIBLING_REQUEST_ID = 13
private const val EPISODE_COUNT = 13
private const val REQUESTER_ID = 7
private const val MODERATOR_ID = 9

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

/**
 * The settled request with nothing left for this viewer to do: no moderation action, no media record
 * to manage, and no edit — the one arm the pinned footer renders neither itself nor the scroll's
 * matching bottom padding for.
 */
internal fun noPrimaryActionDetail(): RequestDetail =
    detail(
        actions = RequestActions(),
        status = SeerrRequestStatusCode.Approved,
        mediaStatus = SeerrMediaStatusCode.Available,
        canEdit = false,
        canManageMedia = false,
    )

/**
 * The settled request with two siblings against the same title: one declined, one completed in 4K.
 *
 * Trimmed of the destination and watch-data facts [detail] carries — with those in, the "Also
 * requested" section falls below the single `phone`-height frame this state takes, so this fixture
 * would render no differently whether the mapping worked or not.
 */
internal fun detailWithSiblings(): RequestDetail =
    detail(
        actions = RequestActions(canRemove = true, canBlock = true),
        status = SeerrRequestStatusCode.Approved,
        mediaStatus = SeerrMediaStatusCode.Available,
        canEdit = false,
        modifiedBy = null,
        modifiedById = null,
        updatedAtMillis = null,
        destination = null,
        watch = null,
        seasons = emptyList(),
        overview = null,
        siblings =
            listOf(
                SiblingRequest(
                    id = SIBLING_REQUEST_ID,
                    status = SeerrRequestStatusCode.Declined,
                    requestedBy = "Grace",
                    requestedAtMillis = SIBLING_DECLINED_AT_MILLIS,
                    is4k = false,
                ),
                SiblingRequest(
                    id = OTHER_SIBLING_REQUEST_ID,
                    status = SeerrRequestStatusCode.Completed,
                    requestedBy = null,
                    requestedAtMillis = SIBLING_COMPLETED_AT_MILLIS,
                    is4k = true,
                ),
            ),
    )

private fun detail(
    actions: RequestActions,
    status: SeerrRequestStatusCode,
    mediaStatus: SeerrMediaStatusCode,
    canEdit: Boolean,
    modifiedBy: String? = "Grace",
    modifiedById: Int? = MODERATOR_ID,
    updatedAtMillis: Long? = UPDATED_AT_MILLIS,
    destination: RequestDestination? =
        RequestDestination(serverName = "Sonarr", profileName = "HD-1080p", rootFolder = "/tv", tags = listOf("kids")),
    watch: WatchStats? = WatchStats(PLAYS, PLAYS_7, PLAYS_30, listOf("Ada", "Grace")),
    seasons: List<SeasonState> = listOf(SeasonState(number = 1, name = null, episodeCount = EPISODE_COUNT, status = mediaStatus)),
    overview: String? = OVERVIEW,
    siblings: List<SiblingRequest> = emptyList(),
    canManageMedia: Boolean = true,
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
                requestedById = REQUESTER_ID,
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
        overview = overview,
        modifiedBy = modifiedBy,
        modifiedById = modifiedById,
        // A moderator's own page: their id links "Requested by" (it is their own request), and
        // `MANAGE_USERS` links "Moderated by" too.
        viewerId = REQUESTER_ID,
        canManageUsers = true,
        updatedAtMillis = updatedAtMillis,
        seasons = seasons,
        destination = destination,
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
                            watch = if (canManageMedia) watch else null,
                        ),
                    ),
                canSetStatus = canManageMedia,
                canClearData = canManageMedia,
                canDeleteFiles = canManageMedia,
            ),
        siblings = siblings,
    )
