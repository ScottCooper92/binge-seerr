package io.github.scottcooper92.binge.seerr.ui.debug

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.MediaInstance
import io.github.scottcooper92.binge.seerr.ui.requests.MediaRecord
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSheetModel

private const val HOUR_MILLIS = 60 * 60 * 1000L

internal fun ManageSheetScenario.model(): RequestSheetModel =
    when (this) {
        ManageSheetScenario.PendingModerator ->
            RequestSheetModel(
                item = item(SeerrRequestStatusCode.Pending, SeerrMediaStatusCode.Pending),
                actions = RequestActions(canApprove = true, canDecline = true, canRemove = true, canBlock = true),
                canEdit = true,
                media = media(standard = SeerrMediaStatusCode.Pending, canManage = true),
            )
        ManageSheetScenario.PendingOwn ->
            RequestSheetModel(
                item = item(SeerrRequestStatusCode.Pending, SeerrMediaStatusCode.Pending),
                actions = RequestActions(canRemove = true),
                canEdit = true,
            )
        ManageSheetScenario.Failed ->
            RequestSheetModel(
                item = item(SeerrRequestStatusCode.Failed, SeerrMediaStatusCode.Processing),
                actions = RequestActions(canRetry = true, canRemove = true, canBlock = true),
                media = media(standard = SeerrMediaStatusCode.Processing, canManage = true),
            )
        ManageSheetScenario.Available ->
            RequestSheetModel(
                item = item(SeerrRequestStatusCode.Completed, SeerrMediaStatusCode.Available),
                actions = RequestActions(canRemove = true, canBlock = true),
                media =
                    media(
                        standard = SeerrMediaStatusCode.Available,
                        fourK = SeerrMediaStatusCode.PartiallyAvailable,
                        canManage = true,
                        canDeleteFiles = true,
                    ),
            )
    }

private fun item(
    status: SeerrRequestStatusCode,
    mediaStatus: SeerrMediaStatusCode,
) = RequestItem(
    id = 1,
    tmdbId = 969681,
    mediaType = RequestMediaType.Movie,
    title = "Spider-Man: Brand New Day",
    posterUrl = null,
    year = "2026",
    requestedBy = "Alex",
    requestedById = 2,
    requestedAtMillis = System.currentTimeMillis() - 2 * HOUR_MILLIS,
    status = status,
    mediaStatus = mediaStatus,
    download = null,
    seasonNumbers = emptyList(),
    is4k = false,
)

private fun media(
    standard: SeerrMediaStatusCode,
    fourK: SeerrMediaStatusCode? = null,
    canManage: Boolean,
    canDeleteFiles: Boolean = false,
) = MediaRecord(
    mediaId = 42,
    isTv = false,
    instances =
        listOfNotNull(
            MediaInstance(is4k = false, status = standard, serviceUrl = null, mediaServerUrl = null, watch = null),
            fourK?.let { MediaInstance(is4k = true, status = it, serviceUrl = null, mediaServerUrl = null, watch = null) },
        ),
    canSetStatus = canManage,
    canClearData = canManage,
    canDeleteFiles = canDeleteFiles,
)
