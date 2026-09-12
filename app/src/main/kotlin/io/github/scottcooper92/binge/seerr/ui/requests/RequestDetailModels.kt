package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueTypeCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.ui.Choice

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

/**
 * One season of a show in the editor. [heldStatus] is set where the server already has it, is
 * fetching it, or another request covers it; a held season cannot be ticked or unticked here.
 */
data class SeasonChoice(
    val number: Int,
    val name: String?,
    val episodeCount: Int,
    val selected: Boolean,
    val heldStatus: SeerrMediaStatusCode? = null,
) {
    val locked: Boolean get() = heldStatus != null
}

/** The destination pickers for a request not yet sent to its client; null where the user may not choose one. */
data class DestinationChoices(
    val servers: List<Choice>,
    val serverId: Int?,
    val profiles: List<Choice>,
    val profileId: Int?,
    val rootFolders: List<String>,
    val rootFolder: String?,
    val tags: List<Choice>,
    val tagIds: Set<Int>,
    val loadingChoices: Boolean = false,
)

data class EditState(
    val seasons: List<SeasonChoice>,
    val destination: DestinationChoices?,
    val saving: Boolean = false,
    /** The show's season list failed to load, so [seasons] is empty for lack of data, not because there is none. */
    val seasonsUnknown: Boolean = false,
) {
    /** A show with nothing ticked is a request for nothing, which the server refuses. */
    val canSave: Boolean
        get() =
            !saving &&
                !seasonsUnknown &&
                destination?.loadingChoices != true &&
                (seasons.isEmpty() || seasons.any { it.selected && !it.locked })
}

data class RequestDetail(
    val item: RequestItem,
    val actions: RequestActions,
    /** Pending, and the user's own or a moderator's to change. */
    val canEdit: Boolean,
    /** `REQUEST_ADVANCED`: the destination pickers alongside the seasons. */
    val canEditDestination: Boolean,
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
        /** The editor while it is open. */
        val edit: EditState? = null,
    ) : RequestDetailUiState

    data class Error(
        val error: SeerrError,
    ) : RequestDetailUiState
}
