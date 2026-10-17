package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.BlockTitleRequest
import com.binge.companion.contracts.request.v1.BlockTitleResponse
import com.binge.companion.contracts.request.v1.ReportIssueRequest
import com.binge.companion.contracts.request.v1.ReportIssueResponse
import com.binge.companion.contracts.request.v1.UnblockTitleRequest
import com.binge.companion.contracts.request.v1.UnblockTitleResponse
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrAddToBlocklistBody
import com.cooper.binge.companion.seerr.seerr.SeerrCreateIssueBody
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
        connection.api().createIssue(SeerrCreateIssueBody(mediaId, request.type.toSeerrIssueType(), request.message))
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
