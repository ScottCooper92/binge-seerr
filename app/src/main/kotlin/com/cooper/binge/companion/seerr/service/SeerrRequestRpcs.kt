package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.ApproveRequestRequest
import com.binge.companion.contracts.request.v1.ApproveRequestResponse
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.CancelRequestRequest
import com.binge.companion.contracts.request.v1.CancelRequestResponse
import com.binge.companion.contracts.request.v1.DeclineRequestRequest
import com.binge.companion.contracts.request.v1.DeclineRequestResponse
import com.binge.companion.contracts.request.v1.EditRequestRequest
import com.binge.companion.contracts.request.v1.EditRequestResponse
import com.binge.companion.contracts.request.v1.GetAdvancedRequestOptionsRequest
import com.binge.companion.contracts.request.v1.GetAdvancedRequestOptionsResponse
import com.binge.companion.contracts.request.v1.GetDestinationOptionsRequest
import com.binge.companion.contracts.request.v1.GetDestinationOptionsResponse
import com.binge.companion.contracts.request.v1.RetryRequestRequest
import com.binge.companion.contracts.request.v1.RetryRequestResponse
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestRequest
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestResponse
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestResponse
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.seerr.BlocklistedTitleException
import com.cooper.binge.companion.seerr.seerr.HTTP_ACCEPTED
import com.cooper.binge.companion.seerr.seerr.HTTP_CONFLICT
import com.cooper.binge.companion.seerr.seerr.SeerrEditRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.VERSION_ID_4K
import com.cooper.binge.companion.seerr.seerr.VERSION_ID_STANDARD
import com.cooper.binge.companion.seerr.seerr.advancedRequestOptions
import com.cooper.binge.companion.seerr.seerr.checkHasSeasons
import com.cooper.binge.companion.seerr.seerr.checkedSeasonNumbers
import com.cooper.binge.companion.seerr.seerr.destinationOptions
import com.cooper.binge.companion.seerr.seerr.isSeerrTv
import com.cooper.binge.companion.seerr.seerr.refusedWithLiveSession
import com.cooper.binge.companion.seerr.seerr.resolveAdvancedDestination
import com.cooper.binge.companion.seerr.seerr.seerrMediaType
import com.cooper.binge.companion.seerr.seerr.standardVersion
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.updateRequest
import io.grpc.Status
import io.grpc.StatusException
import retrofit2.HttpException

/** The REQUEST v1 writes that make, moderate and change a request, and the advanced path's reads. [SeerrRequestService] gates them. */
internal class SeerrRequestRpcs(
    private val connection: SeerrConnection,
    private val statusReads: SeerrStatusReads,
    private val statusCache: MediaStatusStore,
    private val requestCache: RequestStore,
) {
    suspend fun submitRequest(request: SubmitRequestRequest): SubmitRequestResponse {
        // Re-checked here, not trusted to the host. version_id is a field on a core rpc, not a gated rpc,
        // so the contract calls an unlisted one a bad argument (INVALID_ARGUMENT), not PERMISSION_DENIED.
        val is4k =
            when (request.versionId) {
                VERSION_ID_STANDARD -> false
                VERSION_ID_4K -> true
                else -> throw invalidArgument("version_id \"${request.versionId}\" is not one this integration listed")
            }
        if (is4k && !connection.mayRequest4k(request.media)) {
            throw invalidArgument("version_id names 4K, but 4K requests are not open to this user for this media type")
        }
        val media = request.media
        refuseIfKnownBlocklisted(media)
        val seasons = checkedSubmitSeasons(media, request.seasonNumbersList)
        val body =
            SeerrRequestBody(
                mediaType = media.seerrMediaType(),
                mediaId = media.tmdbId,
                seasons = seasons.takeIf { it.isNotEmpty() },
                is4k = is4k,
            )
        return submitAndRespond(media, body)
    }

    /**
     * Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. Every server this user may send this media's
     * shape to (a 4K one only where they may request 4K of its type), and the preselected one's
     * profile/root-folder choices — the same destination a plain [submitRequest] (never 4K) would
     * have used.
     */
    suspend fun getAdvancedRequestOptions(request: GetAdvancedRequestOptionsRequest): GetAdvancedRequestOptionsResponse {
        val isTv = request.media.seerrMediaType().isSeerrTv()
        return GetAdvancedRequestOptionsResponse
            .newBuilder()
            .setDestination(connection.api().advancedRequestOptions(isTv, allow4k = connection.mayRequest4k(request.media)))
            .build()
    }

    /** Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. Re-resolves the profile/root-folder axes for the request's `server_id`. */
    suspend fun getDestinationOptions(request: GetDestinationOptionsRequest): GetDestinationOptionsResponse {
        val isTv = request.media.seerrMediaType().isSeerrTv()
        return GetDestinationOptionsResponse
            .newBuilder()
            .setDestination(
                connection.api().destinationOptions(isTv, request.serverId, allow4k = connection.mayRequest4k(request.media)),
            ).build()
    }

    /**
     * Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. An empty axis on the request falls back to
     * the integration's own default for it — the same one [getAdvancedRequestOptions] preselected
     * — so a submit that never touched a picker is a plain request in every way but its path.
     */
    suspend fun submitAdvancedRequest(request: SubmitAdvancedRequestRequest): SubmitAdvancedRequestResponse {
        val media = request.media
        refuseIfKnownBlocklisted(media)
        val isTv = media.seerrMediaType().isSeerrTv()
        val seasons = checkedSubmitSeasons(media, request.seasonNumbersList)
        // 4K here is a property of the server the caller named. A user who may not request 4K is
        // never offered a 4K server, so naming one is INVALID_ARGUMENT, like any server not offered.
        val destination =
            connection.api().resolveAdvancedDestination(
                isTv,
                request.serverId,
                request.profileId,
                request.rootFolderId,
                allow4k = connection.mayRequest4k(media),
            )
        val body =
            SeerrRequestBody(
                mediaType = media.seerrMediaType(),
                mediaId = media.tmdbId,
                seasons = seasons.takeIf { it.isNotEmpty() },
                is4k = destination.server.is4k,
                serverId = destination.server.id,
                profileId = destination.profileId,
                rootFolder = destination.rootFolder,
            )
        return SubmitAdvancedRequestResponse.newBuilder().setResult(submitAndRespond(media, body)).build()
    }

    /**
     * A submit's seasons, refused as INVALID_ARGUMENT before anything is posted if they are malformed or name a season the
     * show does not have (#1002), the same rule [editRequest] applies. No seasons means the whole show, so nothing is looked up.
     */
    private suspend fun checkedSubmitSeasons(
        media: MediaId,
        numbers: List<Int>,
    ): List<Int> {
        val seasons = numbers.checkedSeasonNumbers()
        if (seasons.isNotEmpty() && media.seerrMediaType().isSeerrTv()) {
            connection.api().tvDetails(media.tmdbId).checkHasSeasons(seasons)
        }
        return seasons
    }

    /** The part of a submit that does not depend on where the body came from: post, read the outcome off the status code, attach the fresh status. */
    private suspend fun submitAndRespond(
        media: MediaId,
        body: SeerrRequestBody,
    ): SubmitRequestResponse {
        val response = connection.api().requestMedia(body)
        statusCache.clearAll()
        val builder = SubmitRequestResponse.newBuilder()
        when {
            response.code() == HTTP_ACCEPTED -> Unit
            response.isSuccessful -> response.body()?.id?.let { builder.setRequestId(it.toString()) }
            response.code() == HTTP_CONFLICT -> builder.setAlreadyRequested(true)
            else -> throw HttpException(response)
        }
        return builder.setStatus(statusReads.status(media)).build()
    }

    /**
     * A title the cache already knows is blocklisted is refused here, without asking Seerr: FAILED_PRECONDITION, as
     * the contract says (#682). A cold cache falls through to Seerr, whose own refusal maps the same way.
     */
    private suspend fun refuseIfKnownBlocklisted(media: MediaId) {
        if (statusReads
                .cachedStatus(media)
                ?.status
                ?.standardVersion()
                ?.availability == Availability.AVAILABILITY_BLOCKLISTED
        ) {
            throw BlocklistedTitleException()
        }
    }

    /**
     * The server answers 401 when this user may not delete the request. Seerr and Jellyseerr refuse a request that is
     * not theirs or is no longer pending; Overseerr refuses only one that is both. That is not the session, which the interceptor confirms against `auth/me` (#997). The request is read
     * only then, to tell the contract's two answers apart: FAILED_PRECONDITION for a request past pending, and
     * PERMISSION_DENIED for one that is pending and someone else's.
     */
    suspend fun cancelRequest(request: CancelRequestRequest): CancelRequestResponse {
        val api = connection.api()
        val requestId = request.requestId.toSeerrRequestId()
        try {
            api.deleteRequest(requestId)
        } catch (e: HttpException) {
            if (!e.refusedWithLiveSession()) throw e
            // A request with no status is pending, as everywhere else; one that will not read is taken as pending too.
            val status = attempt { api.request(requestId).status }.getOrNull()
            val pending = status == null || status == SeerrRequestStatusCode.Pending
            throw StatusException(
                if (pending) {
                    Status.PERMISSION_DENIED.withDescription("This user may not cancel that request")
                } else {
                    Status.FAILED_PRECONDITION.withDescription("Only a pending request can be cancelled")
                },
            )
        }
        requestCache.delete(requestId)
        return CancelRequestResponse.getDefaultInstance()
    }

    suspend fun approveRequest(request: ApproveRequestRequest): ApproveRequestResponse {
        val requestId = request.requestId.toSeerrRequestId()
        connection.api().approveRequest(requestId)
        requestCache.updateStatus(requestId, SeerrRequestStatusCode.Approved.raw)
        return ApproveRequestResponse.getDefaultInstance()
    }

    suspend fun declineRequest(request: DeclineRequestRequest): DeclineRequestResponse {
        val requestId = request.requestId.toSeerrRequestId()
        connection.api().declineRequest(requestId)
        requestCache.updateStatus(requestId, SeerrRequestStatusCode.Declined.raw)
        return DeclineRequestResponse.getDefaultInstance()
    }

    suspend fun retryRequest(request: RetryRequestRequest): RetryRequestResponse {
        connection.api().retryRequest(request.requestId.toSeerrRequestId())
        return RetryRequestResponse.getDefaultInstance()
    }

    /**
     * The request is read first because Seerr's update wants the media type on the body and keeps
     * nothing else stable across an edit without it; the 4K flag and the destination ride along
     * unchanged, since the server assigns each of them from the body and clears the ones it does not
     * find. An empty set or a movie is refused here — the contract's INVALID_ARGUMENT — rather than
     * sent for the server to reject in its own words.
     */
    suspend fun editRequest(request: EditRequestRequest): EditRequestResponse {
        if (request.seasonNumbersList.isEmpty()) throw invalidArgument("a request covers at least one season")
        val seasons = request.seasonNumbersList.checkedSeasonNumbers()
        val api = connection.api()
        val requestId = request.requestId.toSeerrRequestId()
        val current = api.request(requestId)
        if (!current.media.mediaType.isSeerrTv()) throw invalidArgument("only a TV request has seasons to edit")
        // The contract's INVALID_ARGUMENT for "a season the show does not have", which Seerr itself never checks.
        api.tvDetails(current.media.tmdbId).checkHasSeasons(seasons)
        val body =
            SeerrEditRequestBody(
                mediaType = current.media.mediaType,
                seasons = seasons,
                is4k = current.is4k,
                serverId = current.serverId,
                profileId = current.profileId,
                rootFolder = current.rootFolder,
                tags = current.tags,
            )
        api.updateRequest(requestId, body)
        return EditRequestResponse.getDefaultInstance()
    }
}

/**
 * Whether this user may request 4K of [media]'s type, which decides a 4K `version_id` on a submit, the 4K servers on the advanced
 * path and whether a 4K-only title is reportable. Narrower than the handshake's 4K version, which is listed where
 * either type is open: see [SeerrPermissions.mayRequest4k].
 */
internal suspend fun SeerrConnection.mayRequest4k(media: MediaId): Boolean =
    authenticatedUser().toPermissions().mayRequest4k(media.seerrMediaType().isSeerrTv(), profile().settings)
