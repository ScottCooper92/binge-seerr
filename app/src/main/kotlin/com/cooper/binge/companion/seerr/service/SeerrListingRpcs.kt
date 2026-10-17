package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.GetStatusesRequest
import com.binge.companion.contracts.request.v1.GetStatusesResponse
import com.binge.companion.contracts.request.v1.ListRequestsRequest
import com.binge.companion.contracts.request.v1.ListRequestsResponse
import com.binge.companion.contracts.request.v1.RequestEntry
import com.binge.companion.contracts.request.v1.TitleStatus
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.seerrMediaType
import com.cooper.binge.companion.seerr.seerr.toMediaId
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toRequestInfo

/** The REQUEST v1 reads that answer many titles at once: a page of requests, a batch of statuses. [SeerrRequestService] gates them. */
internal class SeerrListingRpcs(
    private val connection: SeerrConnection,
    private val statusReads: SeerrStatusReads,
) {
    /**
     * Requires CAPABILITY_LIST_REQUESTS. One page of Seerr's `GET /request`, filtered as
     * [SeerrRequestQuery] describes and paged by [ListRequestsPageToken]. Each entry's status is the
     * title's, read as [SeerrRequestService.getStatus] reads it, so a title's row in the status cache serves both. A title
     * listed twice on a page (a standard and a 4K request) is looked up once.
     *
     * AWAITING_MODERATION is empty for a user who may not moderate, without asking Seerr: the server
     * would answer with that user's own pending requests, which are not waiting on them.
     */
    suspend fun listRequests(request: ListRequestsRequest): ListRequestsResponse {
        val query = request.filter.toSeerrQuery()
        val pageSize = request.effectivePageSize()
        val skip = ListRequestsPageToken.skipOf(request)
        val user = connection.authenticatedUser()
        val permissions = user.toPermissions()
        return if (query == SeerrRequestQuery.AwaitingModeration && !permissions.canManageRequests) {
            ListRequestsResponse.getDefaultInstance()
        } else {
            val page =
                connection.api().requests(
                    take = pageSize,
                    skip = skip,
                    filter = query.filter,
                    sort = query.sort,
                    requestedBy = user.id.takeIf { query.mine },
                )
            val listed = page.results.mapNotNull { dto -> dto.media.toMediaId()?.let { it to dto } }
            val statuses = statusReads.statusesOf(listed.map { it.first })
            val profile = connection.profile()
            val response = ListRequestsResponse.newBuilder()
            listed.forEach { (media, dto) ->
                val info = permissions.withAllowedActions(dto.toRequestInfo(), own = dto.requestedBy?.id == user.id, profile = profile)
                response.addEntries(
                    RequestEntry
                        .newBuilder()
                        .setMedia(media)
                        .setStatus(statuses.getValue(media))
                        .setRequest(info),
                )
            }
            val next = skip + page.results.size
            if (page.results.isNotEmpty() && next < page.pageInfo.results) {
                response.setNextPageToken(ListRequestsPageToken.issue(request, next))
            }
            response.build()
        }
    }

    /**
     * Requires CAPABILITY_BATCH_STATUS. [SeerrRequestService.getStatus] for each title, through the same cache. Every
     * title is checked before any is looked up, so an unusable one fails the call without a request
     * to Seerr. A title named twice is looked up once and answered twice, as the contract says.
     */
    suspend fun getStatuses(request: GetStatusesRequest): GetStatusesResponse {
        if (request.mediaCount > MAX_STATUSES_PER_CALL) {
            throw invalidArgument("at most $MAX_STATUSES_PER_CALL titles per call, was ${request.mediaCount}")
        }
        request.mediaList.forEach { it.seerrMediaType() }
        val statuses = statusReads.statusesOf(request.mediaList)
        val response = GetStatusesResponse.newBuilder()
        request.mediaList.forEach { media ->
            response.addStatuses(
                TitleStatus
                    .newBuilder()
                    .setMedia(media)
                    .setStatus(statuses.getValue(media)),
            )
        }
        return response.build()
    }
}
