package io.github.scottcooper92.binge.seerr.service

import com.binge.companion.contracts.request.v1.ApprovalState
import com.binge.companion.contracts.request.v1.ApproveRequestRequest
import com.binge.companion.contracts.request.v1.ApproveRequestResponse
import com.binge.companion.contracts.request.v1.Attention
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.BlockTitleRequest
import com.binge.companion.contracts.request.v1.BlockTitleResponse
import com.binge.companion.contracts.request.v1.CancelRequestRequest
import com.binge.companion.contracts.request.v1.CancelRequestResponse
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.DeclineRequestRequest
import com.binge.companion.contracts.request.v1.DeclineRequestResponse
import com.binge.companion.contracts.request.v1.EditRequestRequest
import com.binge.companion.contracts.request.v1.EditRequestResponse
import com.binge.companion.contracts.request.v1.GetAdvancedRequestOptionsRequest
import com.binge.companion.contracts.request.v1.GetAdvancedRequestOptionsResponse
import com.binge.companion.contracts.request.v1.GetAttentionRequest
import com.binge.companion.contracts.request.v1.GetAttentionResponse
import com.binge.companion.contracts.request.v1.GetDestinationOptionsRequest
import com.binge.companion.contracts.request.v1.GetDestinationOptionsResponse
import com.binge.companion.contracts.request.v1.GetStatusRequest
import com.binge.companion.contracts.request.v1.GetStatusResponse
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.IssueType
import com.binge.companion.contracts.request.v1.ObserveAttentionRequest
import com.binge.companion.contracts.request.v1.ObserveAttentionResponse
import com.binge.companion.contracts.request.v1.ObserveStatusRequest
import com.binge.companion.contracts.request.v1.ObserveStatusResponse
import com.binge.companion.contracts.request.v1.ReportIssueRequest
import com.binge.companion.contracts.request.v1.ReportIssueResponse
import com.binge.companion.contracts.request.v1.RequestInfo
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.RetryRequestRequest
import com.binge.companion.contracts.request.v1.RetryRequestResponse
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestRequest
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestResponse
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestResponse
import com.binge.companion.contracts.request.v1.UnblockTitleRequest
import com.binge.companion.contracts.request.v1.UnblockTitleResponse
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.sdk.handshakeResponse
import com.binge.companion.sdk.requireDeclared
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.CachedStatus
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
import io.github.scottcooper92.binge.seerr.data.NoMediaStatusStore
import io.github.scottcooper92.binge.seerr.seerr.SeerrAddToBlocklistBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrCreateIssueBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrEditRequestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueTypeCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaIds
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.advancedRequestOptions
import io.github.scottcooper92.binge.seerr.seerr.destinationOptions
import io.github.scottcooper92.binge.seerr.seerr.details
import io.github.scottcooper92.binge.seerr.seerr.isSeerrTv
import io.github.scottcooper92.binge.seerr.seerr.requesterIds
import io.github.scottcooper92.binge.seerr.seerr.resolveAdvancedDestination
import io.github.scottcooper92.binge.seerr.seerr.seerrMediaType
import io.github.scottcooper92.binge.seerr.seerr.statusCatching
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toRequestStatus
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

/** Seerr returns 202 Accepted when there was nothing left to request, and created nothing. */
private const val HTTP_ACCEPTED = 202

/** Seerr returns 409 Conflict when the media has already been requested. */
private const val HTTP_CONFLICT = 409

/**
 * REQUEST v1, served against the connected Seerr server.
 *
 * Capabilities come from the signed-in user's permission bitmask narrowed by what the server has
 * (its lineage and version, `SeerrServerProfile`), so the host offers exactly what this user may
 * do on this server. Every failure leaves as a gRPC status code (`SeerrErrors.kt`), and every
 * gated rpc re-checks its capability rather than trusting the host to have honoured the handshake.
 *
 * One function per rpc, which is why the function count is suppressed rather than reduced: REQUEST
 * v1 decides this surface, and a companion that implements fewer of them is a companion that does
 * not implement the contract.
 */
@Suppress("TooManyFunctions")
class SeerrRequestService(
    private val connection: SeerrConnection,
    private val versionName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val observeIntervalMillis: Long = OBSERVE_INTERVAL_MILLIS,
    private val attentionIntervalMillis: Long = ATTENTION_INTERVAL_MILLIS,
    private val statusCache: MediaStatusStore = NoMediaStatusStore,
) : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
    private val mediaIds = SeerrMediaIds { connection.api() }
    private val freshness = MediaStatusFreshness(observeIntervalMillis)

    override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
        statusCatching {
            val profile = connection.profile()
            handshakeResponse(
                capabilities = permissions().toCapabilities(profile),
                providerName = profile.variant.displayName,
                companionVersionName = versionName,
            )
        }

    override suspend fun submitRequest(request: SubmitRequestRequest): SubmitRequestResponse =
        statusCatching {
            val media = request.media
            val body =
                SeerrRequestBody(
                    mediaType = media.seerrMediaType(),
                    mediaId = media.tmdbId,
                    seasons = request.seasonNumbersList.takeIf { it.isNotEmpty() },
                    is4k = request.is4K,
                )
            submitAndRespond(media, body)
        }

    /**
     * Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. Every server this media's shape may go to,
     * and the preselected one's profile/root-folder choices — the same destination a plain
     * [submitRequest] (never 4K) would have used.
     */
    override suspend fun getAdvancedRequestOptions(request: GetAdvancedRequestOptionsRequest): GetAdvancedRequestOptionsResponse =
        gatedRead(Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) {
            val isTv = request.media.seerrMediaType().isSeerrTv()
            GetAdvancedRequestOptionsResponse
                .newBuilder()
                .setDestination(connection.api().advancedRequestOptions(isTv))
                .build()
        }

    /** Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. Re-resolves the profile/root-folder axes for the request's `server_id`. */
    override suspend fun getDestinationOptions(request: GetDestinationOptionsRequest): GetDestinationOptionsResponse =
        gatedRead(Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) {
            val isTv = request.media.seerrMediaType().isSeerrTv()
            GetDestinationOptionsResponse
                .newBuilder()
                .setDestination(connection.api().destinationOptions(isTv, request.serverId))
                .build()
        }

    /**
     * Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. An empty axis on the request falls back to
     * the integration's own default for it — the same one [getAdvancedRequestOptions] preselected
     * — so a submit that never touched a picker is a plain request in every way but its path.
     */
    override suspend fun submitAdvancedRequest(request: SubmitAdvancedRequestRequest): SubmitAdvancedRequestResponse =
        gated(Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) {
            val media = request.media
            val isTv = media.seerrMediaType().isSeerrTv()
            val destination = connection.api().resolveAdvancedDestination(isTv, request.serverId, request.profileId, request.rootFolderId)
            val body =
                SeerrRequestBody(
                    mediaType = media.seerrMediaType(),
                    mediaId = media.tmdbId,
                    seasons = request.seasonNumbersList.takeIf { it.isNotEmpty() },
                    is4k = destination.server.is4k,
                    serverId = destination.server.id,
                    profileId = destination.profileId,
                    rootFolder = destination.rootFolder,
                )
            SubmitAdvancedRequestResponse.newBuilder().setResult(submitAndRespond(media, body)).build()
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
            response.isSuccessful -> response.body()?.id?.let(builder::setRequestId)
            response.code() == HTTP_CONFLICT -> builder.setAlreadyRequested(true)
            else -> throw HttpException(response)
        }
        return builder.setStatus(status(media)).build()
    }

    override suspend fun getStatus(request: GetStatusRequest): GetStatusResponse =
        statusCatching { GetStatusResponse.newBuilder().setStatus(status(request.media)).build() }

    /**
     * Polls the server on this app's own cadence and pushes a status only when it changed. The
     * contract leaves cadence to the integration — it knows its server — which is why there is
     * nothing to negotiate. Ends only when the host cancels.
     */
    override fun observeStatus(request: ObserveStatusRequest): Flow<ObserveStatusResponse> =
        flow {
            // Only the first emission may come from the cache: after that this stream is what keeps
            // the row warm, and a poll answering from the row it wrote would never see the server.
            var fromCache = true
            while (true) {
                emit(statusCatching { status(request.media, allowCached = fromCache) })
                fromCache = false
                delay(observeIntervalMillis)
            }
        }.distinctUntilChanged()
            .map { ObserveStatusResponse.newBuilder().setStatus(it).build() }

    override suspend fun cancelRequest(request: CancelRequestRequest): CancelRequestResponse =
        gated(Capability.CAPABILITY_CANCEL) {
            connection.api().deleteRequest(request.requestId)
            CancelRequestResponse.getDefaultInstance()
        }

    override suspend fun approveRequest(request: ApproveRequestRequest): ApproveRequestResponse =
        gated(Capability.CAPABILITY_APPROVE) {
            connection.api().approveRequest(request.requestId)
            ApproveRequestResponse.getDefaultInstance()
        }

    override suspend fun declineRequest(request: DeclineRequestRequest): DeclineRequestResponse =
        gated(Capability.CAPABILITY_DECLINE) {
            connection.api().declineRequest(request.requestId)
            DeclineRequestResponse.getDefaultInstance()
        }

    override suspend fun retryRequest(request: RetryRequestRequest): RetryRequestResponse =
        gated(Capability.CAPABILITY_RETRY) {
            connection.api().retryRequest(request.requestId)
            RetryRequestResponse.getDefaultInstance()
        }

    /**
     * The request is read first because Seerr's update wants the media type on the body and keeps
     * nothing else stable across an edit without it; the 4K flag and the destination ride along
     * unchanged, since the server assigns each of them from the body and clears the ones it does not
     * find. An empty set or a movie is refused here — the contract's INVALID_ARGUMENT — rather than
     * sent for the server to reject in its own words.
     */
    override suspend fun editRequest(request: EditRequestRequest): EditRequestResponse =
        gated(Capability.CAPABILITY_EDIT_SEASONS) {
            if (request.seasonNumbersList.isEmpty()) throw invalidArgument("a request covers at least one season")
            val api = connection.api()
            val current = api.request(request.requestId)
            if (!current.media.mediaType.isSeerrTv()) throw invalidArgument("only a TV request has seasons to edit")
            val body =
                SeerrEditRequestBody(
                    mediaType = current.media.mediaType,
                    seasons = request.seasonNumbersList,
                    is4k = current.is4k,
                    serverId = current.serverId,
                    profileId = current.profileId,
                    rootFolder = current.rootFolder,
                    tags = current.tags,
                )
            api.editRequest(request.requestId, body)
            EditRequestResponse.getDefaultInstance()
        }

    override suspend fun getAttention(request: GetAttentionRequest): GetAttentionResponse =
        statusCatching { GetAttentionResponse.newBuilder().setAttention(attention()).build() }

    /** Polled on this app's own cadence, like [observeStatus]; a push only when the value changed. */
    override fun observeAttention(request: ObserveAttentionRequest): Flow<ObserveAttentionResponse> =
        flow {
            while (true) {
                emit(statusCatching { attention() })
                delay(attentionIntervalMillis)
            }
        }.distinctUntilChanged()
            .map { ObserveAttentionResponse.newBuilder().setAttention(it).build() }

    /**
     * What waits on the signed-in user: requests to moderate, issues to handle — each counted only
     * where the user holds the permission and the server has the endpoint, so a plain requester is
     * asked for nothing. A 401 here is the contract's `needs_reconnect`: the server is connected and
     * the session is what broke, which only this app's sign-in can mend.
     */
    private suspend fun attention(): Attention {
        val permissions = permissions()
        val profile = connection.profile()
        val api = connection.api()
        return try {
            val pending = if (permissions.canManageRequests) api.requestCount().pending else 0
            val issues = if (permissions.canManageIssues && profile.hasCounts) api.issueCount().open else 0
            Attention
                .newBuilder()
                .setPendingCount(pending + issues)
                .setNeedsReconnect(false)
                .build()
        } catch (e: HttpException) {
            if (e.toSeerrError() != SeerrError.Unauthorized) throw e
            Attention
                .newBuilder()
                .setPendingCount(0)
                .setNeedsReconnect(true)
                .build()
        }
    }

    /** The one operation that needs Seerr's own id space: the server's media record, not the TMDB id. */
    override suspend fun reportIssue(request: ReportIssueRequest): ReportIssueResponse =
        gated(Capability.CAPABILITY_REPORT_ISSUE) {
            val mediaId = mediaIds.mediaRecordId(request.media)
            connection.api().createIssue(SeerrCreateIssueBody(mediaId, request.type.toSeerrIssueType(), request.message))
            ReportIssueResponse.getDefaultInstance()
        }

    override suspend fun blockTitle(request: BlockTitleRequest): BlockTitleResponse =
        gated(Capability.CAPABILITY_BLOCK) {
            val body =
                SeerrAddToBlocklistBody(
                    tmdbId = request.media.tmdbId,
                    mediaType = request.media.seerrMediaType(),
                    title = request.title,
                    user = connection.authenticatedUser().id,
                )
            connection.api().addToBlocklist(connection.profile().blocklistPath, body)
            BlockTitleResponse.getDefaultInstance()
        }

    /** Keyed by TMDB id and media type, as the block was; a title the server has no entry for is its 404, NOT_FOUND. */
    override suspend fun unblockTitle(request: UnblockTitleRequest): UnblockTitleResponse =
        gated(Capability.CAPABILITY_BLOCK) {
            connection.api().removeFromBlocklist(
                connection.profile().blocklistPath,
                request.media.tmdbId,
                request.media.seerrMediaType(),
            )
            UnblockTitleResponse.getDefaultInstance()
        }

    private suspend fun permissions(): SeerrPermissions = connection.authenticatedUser().toPermissions()

    /**
     * The title's status, from the cache while it is young enough for where the title is, and from
     * the server otherwise.
     *
     * Allowed actions are never cached and never stored: the contract defines them as what this
     * user may do right now, so they are recomputed against the live permissions on every read of
     * the row. What is cached is the server's answer alone.
     */
    private suspend fun status(
        media: MediaId,
        allowCached: Boolean = true,
    ): RequestStatus {
        val user = connection.authenticatedUser()
        val server = (if (allowCached) cachedStatus(media) else null) ?: fetchStatus(media)
        return user.toPermissions().withAllowedActions(server, viewerId = user.id, profile = connection.profile())
    }

    private suspend fun cachedStatus(media: MediaId): CachedStatus? = statusCache.find(media)?.takeIf { freshness.isFresh(it, clock()) }

    /**
     * The row carries the download ETA the server's answer was turned into, so a cached one is up
     * to its own maximum age out of date. That age is the poll interval for anything downloading,
     * which is the same staleness a host subscribed to [observeStatus] already lives with.
     */
    private suspend fun fetchStatus(media: MediaId): CachedStatus {
        val now = clock()
        val info = connection.api().details(media).mediaInfo
        val fetched = CachedStatus(info.toRequestStatus(now), now, info.requesterIds())
        if (freshness.maxAgeMillis(fetched.status) != null) statusCache.put(media, fetched)
        return fetched
    }

    private suspend fun <T> gated(
        capability: Capability,
        block: suspend () -> T,
    ): T =
        statusCatching {
            checkDeclared(capability)
            // Every gated rpc is a write, and a write to any title makes every cached row suspect —
            // most of them name a request id rather than a title, so there is nothing narrower to drop.
            block().also { statusCache.clearAll() }
        }

    /** As [gated], for an rpc that only reads: no cache to invalidate behind it. */
    private suspend fun <T> gatedRead(
        capability: Capability,
        block: suspend () -> T,
    ): T =
        statusCatching {
            checkDeclared(capability)
            block()
        }

    private suspend fun checkDeclared(capability: Capability) {
        permissions().toCapabilities(connection.profile()).requireDeclared(capability)
    }

    private companion object {
        const val OBSERVE_INTERVAL_MILLIS = 15_000L

        /** Coarser than a title's status: the host holds this stream open for as long as it runs. */
        const val ATTENTION_INTERVAL_MILLIS = 60_000L
    }
}

private fun invalidArgument(reason: String): StatusException = StatusException(Status.INVALID_ARGUMENT.withDescription(reason))

/**
 * Seerr's permissions as the contract's capability set — the handshake is derived, never
 * hand-listed — narrowed by the server: a blocklist the lineage lacks, or issues an Overseerr is
 * too old for, are not offered however the user's bits read, since the server answers them 404.
 */
fun SeerrPermissions.toCapabilities(profile: SeerrServerProfile): Set<Capability> =
    buildSet {
        add(Capability.CAPABILITY_OBSERVE_STATUS)
        add(Capability.CAPABILITY_ATTENTION)
        if (canRequest4k) add(Capability.CAPABILITY_REQUEST_4K)
        if (canRequestAdvanced) {
            // Both capabilities are declared together during rollout: a host that only knows the
            // older one keeps getting the Activity hand-off, one that knows the new one gets the
            // native picker. Neither is dropped until the cutover (Binge#2882, scope item 7).
            add(Capability.CAPABILITY_ADVANCED_OPTIONS)
            add(Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS)
        }
        if (canManageRequests) addAll(listOf(Capability.CAPABILITY_APPROVE, Capability.CAPABILITY_DECLINE, Capability.CAPABILITY_RETRY))
        // A requester may cancel or reshape their own pending request, and a moderator anyone's.
        if (canRequest || canManageRequests) addAll(listOf(Capability.CAPABILITY_CANCEL, Capability.CAPABILITY_EDIT_SEASONS))
        if (canCreateIssues && profile.hasIssues) add(Capability.CAPABILITY_REPORT_ISSUE)
        if (canManageBlocklist && profile.hasBlocklist) add(Capability.CAPABILITY_BLOCK)
    }

/** The capabilities that act on one existing request, and so travel on that request's own `allowed_actions`. */
private val REQUEST_SCOPED =
    setOf(
        Capability.CAPABILITY_APPROVE,
        Capability.CAPABILITY_DECLINE,
        Capability.CAPABILITY_RETRY,
        Capability.CAPABILITY_CANCEL,
        Capability.CAPABILITY_EDIT_SEASONS,
    )

/**
 * The server's status with its allowed actions filled in for the user [viewerId]. Each request carries
 * its own set. The title's set holds a report only against something available, and the block
 * capability either way: it offers a block on a title and an unblock on a blocked one. Its
 * request-scoped entries are the union of the requests', so a
 * host that reads only the title-level list is never offered an action that every request refuses.
 */
fun SeerrPermissions.withAllowedActions(
    server: CachedStatus,
    viewerId: Int,
    profile: SeerrServerProfile,
): RequestStatus {
    val declared = toCapabilities(profile)
    val status = server.status
    val requests =
        status.requestsList.map { request ->
            val own = server.requesterIds[request.id] == viewerId
            request
                .toBuilder()
                .clearAllowedActions()
                .addAllAllowedActions(requestActions(request, own, declared))
                .build()
        }
    val requestActions = requests.flatMapTo(mutableSetOf()) { it.allowedActionsList }
    val reportable =
        status.availability == Availability.AVAILABILITY_AVAILABLE ||
            status.availability == Availability.AVAILABILITY_PARTIALLY_AVAILABLE
    val titleActions =
        declared.filter { capability ->
            when (capability) {
                in REQUEST_SCOPED -> capability in requestActions
                Capability.CAPABILITY_REPORT_ISSUE -> reportable
                else -> true
            }
        }
    return status
        .toBuilder()
        .clearRequests()
        .addAllRequests(requests)
        .clearAllowedActions()
        .addAllAllowedActions(titleActions)
        .build()
}

/**
 * The checks Seerr makes on one request: approve and decline need a pending request and retry a failed
 * one, each for a moderator. Cancel is a moderator's on any request, or the requester's own while it
 * is pending. An edit is the moderator's or the requester's, only on a pending TV request.
 */
private fun SeerrPermissions.requestActions(
    request: RequestInfo,
    own: Boolean,
    declared: Set<Capability>,
): List<Capability> {
    val pending = request.state.isPending()
    return declared.filter { capability ->
        when (capability) {
            Capability.CAPABILITY_APPROVE, Capability.CAPABILITY_DECLINE -> pending
            Capability.CAPABILITY_RETRY -> request.state.isFailed()
            Capability.CAPABILITY_CANCEL -> canManageRequests || (own && pending)
            Capability.CAPABILITY_EDIT_SEASONS -> request.seasonNumbersCount > 0 && pending && (canManageRequests || own)
            else -> false
        }
    }
}

private fun ApprovalState.isPending() = this == ApprovalState.APPROVAL_STATE_PENDING

private fun ApprovalState.isFailed() = this == ApprovalState.APPROVAL_STATE_FAILED

private fun IssueType.toSeerrIssueType(): SeerrIssueTypeCode =
    when (this) {
        IssueType.ISSUE_TYPE_VIDEO -> SeerrIssueTypeCode.Video
        IssueType.ISSUE_TYPE_AUDIO -> SeerrIssueTypeCode.Audio
        IssueType.ISSUE_TYPE_SUBTITLE -> SeerrIssueTypeCode.Subtitles
        IssueType.ISSUE_TYPE_OTHER, IssueType.ISSUE_TYPE_UNSPECIFIED, IssueType.UNRECOGNIZED -> SeerrIssueTypeCode.Other
    }
