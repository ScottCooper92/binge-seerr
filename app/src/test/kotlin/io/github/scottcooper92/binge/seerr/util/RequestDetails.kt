package io.github.scottcooper92.binge.seerr.util

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.MediaRecord
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDestination
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDownload
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSummary

/**
 * One request, the Approved movie "Heat" (1995) requested by ana: a test names only what it varies. A TMDB id the test
 * does not care about follows [id].
 */
fun requestItem(
    id: Int = 1,
    status: SeerrRequestStatusCode? = SeerrRequestStatusCode.Approved,
    title: String? = "Heat",
    tmdbId: Int = id,
    mediaType: RequestMediaType = RequestMediaType.Movie,
    year: String? = "1995",
    requestedBy: String? = "ana",
    requestedById: Int? = 3,
    mediaStatus: SeerrMediaStatusCode? = null,
    download: RequestDownload? = null,
    is4k: Boolean = false,
): RequestItem =
    RequestItem(
        id = id,
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = title,
        posterUrl = null,
        year = year,
        requestedBy = requestedBy,
        requestedById = requestedById,
        requestedAtMillis = null,
        status = status,
        mediaStatus = mediaStatus,
        download = download,
        seasonNumbers = emptyList(),
        is4k = is4k,
    )

/**
 * The request detail page's whole record around [item], with nothing editable, no destination, seasons or downloads, and
 * a viewer who may do nothing. A test overrides the one or two fields it is about; the rest is the same for every test, so
 * a new field on [RequestDetail] is added here once.
 */
fun requestDetail(
    item: RequestItem = requestItem(),
    actions: RequestActions = RequestActions(),
    canEdit: Boolean = false,
    overview: String? = null,
    viewerId: Int? = 7,
    canManageUsers: Boolean = false,
    destination: RequestDestination? = null,
    mediaId: Int? = 9,
    canReportIssue: Boolean = false,
    media: MediaRecord? = null,
    siblings: List<RequestSummary> = emptyList(),
): RequestDetail =
    RequestDetail(
        item = item,
        actions = actions,
        canEdit = canEdit,
        canEditDestination = false,
        backdropUrl = null,
        overview = overview,
        modifiedBy = null,
        modifiedById = null,
        viewerId = viewerId,
        canManageUsers = canManageUsers,
        updatedAtMillis = null,
        seasons = emptyList(),
        destination = destination,
        downloads = emptyList(),
        mediaId = mediaId,
        canReportIssue = canReportIssue,
        webUrl = "https://seerr.example/movie/${item.id}",
        mediaServerUrl = null,
        serviceUrl = null,
        media = media,
        siblings = siblings,
    )
