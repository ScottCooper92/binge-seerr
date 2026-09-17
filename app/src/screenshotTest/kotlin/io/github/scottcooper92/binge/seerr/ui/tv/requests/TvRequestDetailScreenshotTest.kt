package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.DetailDownload
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.SeasonState
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
        destination = null,
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
}
