package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueTypeCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode

/** One season of a requested show: the request asked for it, and this is where the server has it. */
data class SeasonState(
    val number: Int,
    val name: String?,
    val episodeCount: Int?,
    val status: SeerrMediaStatusCode?,
)

/** Where the request was sent, named where the server's service lists still know the ids. */
data class RequestDestination(
    val serverName: String?,
    val profileName: String?,
    val rootFolder: String?,
    val tags: List<String>,
)

data class DetailDownload(
    val title: String?,
    val fraction: Float,
    val totalBytes: Long?,
    val etaMinutes: Int?,
)

/** What a user may report about a title, in the order the sheet offers them. */
enum class IssueType(
    val code: SeerrIssueTypeCode,
) {
    Video(SeerrIssueTypeCode.Video),
    Audio(SeerrIssueTypeCode.Audio),
    Subtitles(SeerrIssueTypeCode.Subtitles),
    Other(SeerrIssueTypeCode.Other),
}

data class RequestDetail(
    val item: RequestItem,
    val actions: RequestActions,
    val backdropUrl: String?,
    val overview: String?,
    val modifiedBy: String?,
    val updatedAtMillis: Long?,
    val seasons: List<SeasonState>,
    val destination: RequestDestination?,
    val downloads: List<DetailDownload>,
    /** The server's own media id, which an issue is filed against; null for a title it no longer tracks. */
    val mediaId: Int?,
    val canReportIssue: Boolean,
    /** The title in the server's web client, for the hand-off until Binge's own arrives. */
    val webUrl: String,
    val mediaServerUrl: String?,
)

sealed interface IssueReport {
    data object Idle : IssueReport

    data object Sending : IssueReport

    data object Sent : IssueReport

    data class Failed(
        val error: SeerrError,
    ) : IssueReport
}

sealed interface RequestDetailUiState {
    data object Loading : RequestDetailUiState

    data class Ready(
        val detail: RequestDetail,
        val report: IssueReport = IssueReport.Idle,
    ) : RequestDetailUiState

    data class Error(
        val error: SeerrError,
    ) : RequestDetailUiState
}
