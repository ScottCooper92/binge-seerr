package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.requests.DetailDownload
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.ModerationEvent
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDestination
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.requests.SeasonState
import kotlinx.coroutines.flow.emptyFlow

private const val TMDB_ID = 1396
private const val REQUEST_ID = 22
private const val REQUESTED_AT_MILLIS = 1_759_000_000_000L
private const val SEASON_TWO_EPISODES = 10

/** A show with seasons at different states and a download in progress; this viewer moderates it. */
private fun seriesDetail(): RequestDetail =
    RequestDetail(
        item =
            RequestItem(
                id = REQUEST_ID,
                tmdbId = TMDB_ID,
                mediaType = RequestMediaType.Tv,
                title = "The Bear",
                posterUrl = null,
                year = "2022",
                requestedBy = "Ada",
                requestedById = 7,
                requestedAtMillis = REQUESTED_AT_MILLIS,
                status = SeerrRequestStatusCode.Approved,
                mediaStatus = SeerrMediaStatusCode.Processing,
                download = null,
                seasonNumbers = listOf(1, 2),
                is4k = true,
            ),
        actions = RequestActions(canRetry = true, canRemove = true),
        canEdit = false,
        canEditDestination = false,
        backdropUrl = null,
        overview = null,
        modifiedBy = null,
        modifiedById = null,
        viewerId = 7,
        canManageUsers = false,
        updatedAtMillis = null,
        seasons =
            listOf(
                SeasonState(number = 1, name = null, episodeCount = 8, status = SeerrMediaStatusCode.Available),
                SeasonState(number = 2, name = null, episodeCount = SEASON_TWO_EPISODES, status = SeerrMediaStatusCode.Processing),
            ),
        destination =
            RequestDestination(serverName = "Sonarr", profileName = "HD-1080p", rootFolder = "/tv", tags = listOf("kids", "4k-hdr")),
        downloads = listOf(DetailDownload(title = "The Bear S02E05", fraction = 0.42f, totalBytes = 2_400_000_000L, etaMinutes = 18)),
        mediaId = 501,
        canReportIssue = false,
        webUrl = "https://seerr.example/tv/$TMDB_ID",
        mediaServerUrl = null,
        serviceUrl = null,
        media = null,
        siblings = emptyList(),
    )

/** A film: no seasons, no downloads, already available, and nothing for this viewer to moderate. */
private fun filmDetail(): RequestDetail =
    RequestDetail(
        item =
            RequestItem(
                id = REQUEST_ID + 1,
                tmdbId = TMDB_ID + 1,
                mediaType = RequestMediaType.Movie,
                title = "Heat",
                posterUrl = null,
                year = "1995",
                requestedBy = "Grace",
                requestedById = 3,
                requestedAtMillis = REQUESTED_AT_MILLIS,
                status = SeerrRequestStatusCode.Approved,
                mediaStatus = SeerrMediaStatusCode.Available,
                download = null,
                seasonNumbers = emptyList(),
                is4k = false,
            ),
        actions = RequestActions(),
        canEdit = false,
        canEditDestination = false,
        backdropUrl = null,
        overview = null,
        modifiedBy = null,
        modifiedById = null,
        viewerId = 3,
        canManageUsers = false,
        updatedAtMillis = null,
        seasons = emptyList(),
        destination = null,
        downloads = emptyList(),
        mediaId = 900,
        canReportIssue = false,
        webUrl = "https://seerr.example/movie/${TMDB_ID + 1}",
        mediaServerUrl = null,
        serviceUrl = null,
        media = null,
        siblings = emptyList(),
    )

private fun actions(onOpenInBinge: (() -> Unit)?) =
    TvRequestDetailActions(
        onBack = {},
        onRetry = {},
        onOpenInBinge = onOpenInBinge,
        onApprove = {},
        onRetryRequest = {},
        onDecline = {},
        onRemove = {},
        onBlock = {},
        onSetMediaStatus = { _, _, _ -> },
        onReportIssue = { _, _ -> },
        onDismissReport = {},
    )

/**
 * The television request detail page: a series with its seasons and a download, a film with neither, and
 * the Open in Binge button shown against the same film with it hidden — the one comparison that isolates
 * what the button's presence changes.
 */
class TvRequestDetailScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Series() {
        TvRequestDetailScreen(
            state = RequestDetailUiState.Ready(seriesDetail()),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = {}),
        )
    }

    /** A manager's page for a pending request: Approve, Decline, Remove and Block in one row. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Moderator() {
        TvRequestDetailScreen(
            state =
                RequestDetailUiState.Ready(
                    filmDetail().copy(actions = RequestActions(canApprove = true, canDecline = true, canRemove = true, canBlock = true)),
                ),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = {}),
        )
    }

    /**
     * A manager's page for a title the server tracks: Mark as and Report an issue beside the moderation, and the details row
     * naming what stays on the phone (#1036).
     */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MarkAndReport() {
        TvRequestDetailScreen(
            state =
                RequestDetailUiState.Ready(
                    filmDetail().copy(
                        actions = RequestActions(canRemove = true),
                        canEdit = true,
                        canReportIssue = true,
                        media =
                            MediaRecord(
                                mediaId = 900,
                                isTv = false,
                                instances = listOf(MediaInstance(false, SeerrMediaStatusCode.Available, null, null, null)),
                                canSetStatus = true,
                                canClearData = false,
                                canDeleteFiles = false,
                            ),
                    ),
                ),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = {}),
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Film() {
        TvRequestDetailScreen(state = RequestDetailUiState.Ready(filmDetail()), events = emptyFlow(), actions = actions(onOpenInBinge = {}))
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun OpenInBingeHidden() {
        TvRequestDetailScreen(
            state = RequestDetailUiState.Ready(filmDetail()),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = null),
        )
    }

    /** The page could not be read (#1053): the error page over the overlay, with its retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Failed() {
        TvRequestDetailScreen(
            state = RequestDetailUiState.Error(SeerrError.Unreachable),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = {}),
        )
    }

    /**
     * The page before its record has arrived (#1053): the loading plate over the overlay. The tapped row standing in
     * (`Seeded`) draws this same plate here, and `Seeded` with an error draws [Failed]'s, so neither has a frame of its own.
     */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        TvRequestDetailScreen(state = RequestDetailUiState.Loading, events = emptyFlow(), actions = actions(onOpenInBinge = {}))
    }

    /** Remove asks first (#1053): what it does, then Remove and Cancel, over the page. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun RemoveConfirm() {
        TvRequestDetailScreen(
            state = RequestDetailUiState.Ready(filmDetail().copy(actions = RequestActions(canRemove = true, canBlock = true))),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = {}),
            initialSheet = DetailSheet.Remove,
        )
    }

    /** Block asks first too: the title will not be requested again. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun BlockConfirm() {
        TvRequestDetailScreen(
            state = RequestDetailUiState.Ready(filmDetail().copy(actions = RequestActions(canRemove = true, canBlock = true))),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = {}),
            initialSheet = DetailSheet.Block,
        )
    }

    /** An approval that landed: the note at the foot of the page, until it clears a few seconds later (#1053). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ApprovedNote() {
        TvRequestDetailScreen(
            state = RequestDetailUiState.Ready(seriesDetail()),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = {}),
            initialEvent = ModerationEvent.Approved,
        )
    }

    /** A moderation the server refused: the same note, in the error tone, saying why. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun FailedNote() {
        TvRequestDetailScreen(
            state = RequestDetailUiState.Ready(seriesDetail()),
            events = emptyFlow(),
            actions = actions(onOpenInBinge = {}),
            initialEvent = ModerationEvent.Failed(SeerrError.Unreachable),
        )
    }
}
