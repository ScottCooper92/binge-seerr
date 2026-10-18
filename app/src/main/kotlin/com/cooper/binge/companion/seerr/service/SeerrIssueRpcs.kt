package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.BlockTitleRequest
import com.binge.companion.contracts.request.v1.BlockTitleResponse
import com.binge.companion.contracts.request.v1.ReportIssueRequest
import com.binge.companion.contracts.request.v1.ReportIssueResponse
import com.binge.companion.contracts.request.v1.UnblockTitleRequest
import com.binge.companion.contracts.request.v1.UnblockTitleResponse
import com.binge.companion.contracts.v1.MediaType
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrAddToBlocklistBody
import com.cooper.binge.companion.seerr.seerr.SeerrCreateIssueBody
import com.cooper.binge.companion.seerr.seerr.VERSION_ID_4K
import com.cooper.binge.companion.seerr.seerr.addToBlocklistOnce
import com.cooper.binge.companion.seerr.seerr.details
import com.cooper.binge.companion.seerr.seerr.recordIdFor
import com.cooper.binge.companion.seerr.seerr.refusedWithLiveSession
import com.cooper.binge.companion.seerr.seerr.seerrMediaType
import com.cooper.binge.companion.seerr.seerr.standardVersion
import com.cooper.binge.companion.seerr.seerr.toRequestStatus
import com.cooper.binge.companion.seerr.seerr.version4k
import io.grpc.Status
import io.grpc.StatusException
import retrofit2.HttpException

/** The REQUEST v1 writes against a title rather than a request: reporting an issue, and the blocklist. [SeerrRequestService] gates them. */
internal class SeerrIssueRpcs(
    private val connection: SeerrConnection,
    private val clock: () -> Long,
) {
    /**
     * The one operation that needs Seerr's own id space: the server's media record, not the TMDB id.
     *
     * An issue needs something to report against: the contract answers FAILED_PRECONDITION unless the title is
     * available or partially available, an unrequested one included (#682). The 4K version counts for a user
     * who may request 4K of its type (#704, #1604). One details read gives both that and Seerr's own media record id.
     */
    suspend fun reportIssue(request: ReportIssueRequest): ReportIssueResponse {
        request.checkScope()
        val info = connection.api().details(request.media).mediaInfo
        val status = info.toRequestStatus(clock())
        val sees4k = connection.mayRequest4k(request.media)
        if (!status.isReportable(sees4k = sees4k)) {
            // The 4K state is named only to a user who may see it, as withAllowedActions does for every status.
            val fourK = if (sees4k) " (4K: ${status.version4k().availability})" else ""
            throw StatusException(
                Status.FAILED_PRECONDITION.withDescription(
                    "Nothing to report against: the title is ${status.standardVersion().availability}$fourK",
                ),
            )
        }
        val mediaId = info.recordIdFor(request.media)
        val body =
            SeerrCreateIssueBody(
                mediaId = mediaId,
                issueType = request.type.toSeerrIssueType(),
                message = request.message.namingVersion(request.versionId, sees4k),
                problemSeason = request.seasonNumber(),
                problemEpisode = request.episodeNumber(),
            )
        connection.api().createIssue(body)
        return ReportIssueResponse.getDefaultInstance()
    }

    /**
     * A title already on the blocklist is OK and changes nothing, as the contract says (#1000). Seerr refuses the
     * second add instead, and [addToBlocklistOnce] reads that refusal as done. A cached status could not decide this
     * up front: a cold cache knows nothing, and the console may have blocked the title a moment ago.
     */
    suspend fun blockTitle(request: BlockTitleRequest): BlockTitleResponse {
        val body =
            SeerrAddToBlocklistBody(
                tmdbId = request.media.tmdbId,
                mediaType = request.media.seerrMediaType(),
                title = request.title,
                user = connection.authenticatedUser().id,
            )
        connection.api().addToBlocklistOnce(connection.profile().blocklistPath, body)
        return BlockTitleResponse.getDefaultInstance()
    }

    /**
     * Keyed by TMDB id and media type, as the block was. A title the server has no entry for is NOT_FOUND: Seerr 3
     * answers 404, and Jellyseerr 2.x's `blacklist` answers 401 with the session still alive (#998).
     */
    suspend fun unblockTitle(request: UnblockTitleRequest): UnblockTitleResponse {
        try {
            connection.api().removeFromBlocklist(
                connection.profile().blocklistPath,
                request.media.tmdbId,
                connection.profile().unblockMediaType(request.media.seerrMediaType()),
            )
        } catch (e: HttpException) {
            if (!e.refusedWithLiveSession()) throw e
            throw StatusException(Status.NOT_FOUND.withDescription("The title is not on the blocklist"))
        }
        return UnblockTitleResponse.getDefaultInstance()
    }
}

/**
 * The contract's INVALID_ARGUMENTs for an issue's scope, and for a category: this integration declares no
 * `CAPABILITY_ISSUE_CATEGORIES`, since Seerr's issue types are a fixed four, so naming one is a bad argument.
 */
private fun ReportIssueRequest.checkScope() {
    if (categoryId.isNotEmpty()) throw invalidArgument("category_id was set, but this integration lists no issue categories")
    if (hasSeasonNumber() && seasonNumber < 0) throw invalidArgument("season_number is below 0")
    if (hasSeasonNumber() && media.mediaType != MediaType.MEDIA_TYPE_TV) throw invalidArgument("season_number is for a series")
    if (hasEpisodeNumber() && !hasSeasonNumber()) throw invalidArgument("episode_number is set without season_number")
    if (hasEpisodeNumber() && episodeNumber < 0) throw invalidArgument("episode_number is below 0")
}

/** Seerr's "none" is 0, so an unset season is 0 and so is the specials season: the server cannot tell them apart. */
private fun ReportIssueRequest.seasonNumber(): Int = if (hasSeasonNumber()) seasonNumber else 0

private fun ReportIssueRequest.episodeNumber(): Int = if (hasEpisodeNumber()) episodeNumber else 0

/**
 * Seerr stores no version on an issue, so the one the user named travels in the message, where the
 * administrator reads it. Only the 4K version is named, and only to a user who may see it.
 */
private fun String.namingVersion(
    versionId: String,
    sees4k: Boolean,
): String = if (versionId == VERSION_ID_4K && sees4k) "$this\n\n(4K version)" else this
