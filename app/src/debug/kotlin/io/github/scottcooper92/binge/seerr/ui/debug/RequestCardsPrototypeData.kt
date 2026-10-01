package io.github.scottcooper92.binge.seerr.ui.debug

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDestination
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSummary

private const val HOUR_MILLIS = 60 * 60 * 1000L

internal fun cardsPrototypeDetail(): RequestDetail {
    val now = System.currentTimeMillis()
    return RequestDetail(
        item =
            RequestItem(
                id = 1,
                tmdbId = 1399,
                mediaType = RequestMediaType.Tv,
                title = "Severance",
                posterUrl = null,
                year = "2022",
                requestedBy = "Alex",
                requestedById = 2,
                requestedAtMillis = now - 2 * HOUR_MILLIS,
                status = SeerrRequestStatusCode.Approved,
                mediaStatus = SeerrMediaStatusCode.Processing,
                download = null,
                seasonNumbers = listOf(1, 2),
                is4k = true,
            ),
        actions = RequestActions(canApprove = false, canDecline = true, canRemove = true),
        canEdit = false,
        canEditDestination = true,
        backdropUrl = null,
        overview = null,
        modifiedBy = "Scott",
        modifiedById = 1,
        viewerId = 1,
        canManageUsers = true,
        updatedAtMillis = now - HOUR_MILLIS,
        seasons = emptyList(),
        destination =
            RequestDestination(
                serverName = "Sonarr 4K",
                profileName = "Ultra-HD",
                rootFolder = "/data/tv",
                tags = listOf("anime", "kids"),
            ),
        downloads = emptyList(),
        mediaId = 42,
        canReportIssue = true,
        webUrl = "",
        mediaServerUrl = null,
        serviceUrl = null,
        media = null,
        siblings =
            listOf(
                RequestSummary(2, SeerrRequestStatusCode.Declined, "Sam", now - 48 * HOUR_MILLIS, false, listOf(1)),
                RequestSummary(3, SeerrRequestStatusCode.Pending, "Jo", now - 26 * HOUR_MILLIS, false, listOf(3)),
            ),
    )
}
