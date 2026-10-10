package io.github.scottcooper92.binge.seerr.service

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
import com.binge.companion.contracts.request.v1.GetStatusesRequest
import com.binge.companion.contracts.request.v1.GetStatusesResponse
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.ListRequestsRequest
import com.binge.companion.contracts.request.v1.ListRequestsResponse
import com.binge.companion.contracts.request.v1.ObserveAttentionRequest
import com.binge.companion.contracts.request.v1.ObserveAttentionResponse
import com.binge.companion.contracts.request.v1.ObserveStatusRequest
import com.binge.companion.contracts.request.v1.ObserveStatusResponse
import com.binge.companion.contracts.request.v1.ReportIssueRequest
import com.binge.companion.contracts.request.v1.ReportIssueResponse
import com.binge.companion.contracts.request.v1.RequestEntry
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.RetryRequestRequest
import com.binge.companion.contracts.request.v1.RetryRequestResponse
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestRequest
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestResponse
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestResponse
import com.binge.companion.contracts.request.v1.TitleStatus
import com.binge.companion.contracts.request.v1.UnblockTitleRequest
import com.binge.companion.contracts.request.v1.UnblockTitleResponse
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.sdk.handshakeResponse
import com.binge.companion.sdk.requireDeclared
import io.github.scottcooper92.binge.seerr.auth.BingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.NoBingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.NotConnectedException
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.CachedStatus
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
import io.github.scottcooper92.binge.seerr.data.NoMediaStatusStore
import io.github.scottcooper92.binge.seerr.data.NoRequestStore
import io.github.scottcooper92.binge.seerr.data.RequestStore
import io.github.scottcooper92.binge.seerr.seerr.HTTP_CONFLICT
import io.github.scottcooper92.binge.seerr.seerr.SeerrAddToBlocklistBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrCreateIssueBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrEditRequestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.advancedRequestOptions
import io.github.scottcooper92.binge.seerr.seerr.destinationOptions
import io.github.scottcooper92.binge.seerr.seerr.details
import io.github.scottcooper92.binge.seerr.seerr.isSeerrTv
import io.github.scottcooper92.binge.seerr.seerr.recordIdFor
import io.github.scottcooper92.binge.seerr.seerr.rejectsSession
import io.github.scottcooper92.binge.seerr.seerr.requesterIds
import io.github.scottcooper92.binge.seerr.seerr.resolveAdvancedDestination
import io.github.scottcooper92.binge.seerr.seerr.seerrMediaType
import io.github.scottcooper92.binge.seerr.seerr.standardDownload
import io.github.scottcooper92.binge.seerr.seerr.statusCatching
import io.github.scottcooper92.binge.seerr.seerr.toMediaId
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toRequestInfo
import io.github.scottcooper92.binge.seerr.seerr.toRequestStatus
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.seerr.toStatusException
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.operationFailed
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException

/** Seerr returns 202 Accepted when there was nothing left to request, and created nothing. */
private const val HTTP_ACCEPTED = 202

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
    private val bingeConnection: BingeConnectionStore = NoBingeConnectionStore,
    private val analytics: Analytics = NoOpAnalytics,
    private val requestCache: RequestStore = NoRequestStore,
) : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
    private val freshness = MediaStatusFreshness(observeIntervalMillis)

    /**
     * The one place a bound Binge client is known to have handed over its identity, successfully.
     *
     * Re-reads the profile and the signed-in user rather than serving the connection's cached copies:
     * this is where the capability set is decided, and a host that rebinds while the process is alive
     * would otherwise be told what the user could do when the process started.
     *
     * Never UNAUTHENTICATED (binge-companions#106). The host gates every other call on these
     * capabilities, so with nothing connected or a rejected session this still answers OK, declaring
     * only [CAPABILITY_ATTENTION][Capability.CAPABILITY_ATTENTION]: that is how the host reaches
     * `needs_reconnect`, or the UNAUTHENTICATED that sends the user here to connect. Any other
     * failure, an unreachable server say, keeps its own code.
     */
    override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
        reportingFailure("handshake") {
            val profile =
                try {
                    connection.refreshProfile()
                } catch (_: NotConnectedException) {
                    null
                }
            val response =
                handshakeResponse(
                    capabilities = profile?.let { sessionCapabilities(it) } ?: NO_SESSION_CAPABILITIES,
                    providerName = (profile?.variant ?: SeerrVariant.Unknown).displayName,
                    companionVersionName = versionName,
                )
            bingeConnection.recordHandshake()
            response
        }

    private suspend fun sessionCapabilities(profile: SeerrServerProfile): Set<Capability> =
        try {
            connection.refreshAuthenticatedUser().toPermissions().toCapabilities(profile)
        } catch (e: HttpException) {
            if (!e.toSeerrError().rejectsSession) throw e
            NO_SESSION_CAPABILITIES
        }

    override suspend fun submitRequest(request: SubmitRequestRequest): SubmitRequestResponse =
        reportingFailure("submit_request") {
            // Re-checked here, not trusted to the host. is_4k is a flag on a core rpc, not a gated rpc,
            // so the contract calls it a bad argument (INVALID_ARGUMENT) rather than PERMISSION_DENIED.
            if (request.is4K && !mayRequest4k()) {
                throw invalidArgument("is_4k is set, but CAPABILITY_REQUEST_4K was not declared")
            }
            val media = request.media
            refuseIfKnownBlocklisted(media)
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
     * Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. Every server this user may send this media's
     * shape to (a 4K one only with CAPABILITY_REQUEST_4K), and the preselected one's
     * profile/root-folder choices — the same destination a plain [submitRequest] (never 4K) would
     * have used.
     */
    override suspend fun getAdvancedRequestOptions(request: GetAdvancedRequestOptionsRequest): GetAdvancedRequestOptionsResponse =
        gatedRead("get_advanced_request_options", Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) {
            val isTv = request.media.seerrMediaType().isSeerrTv()
            GetAdvancedRequestOptionsResponse
                .newBuilder()
                .setDestination(connection.api().advancedRequestOptions(isTv, allow4k = mayRequest4k()))
                .build()
        }

    /** Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. Re-resolves the profile/root-folder axes for the request's `server_id`. */
    override suspend fun getDestinationOptions(request: GetDestinationOptionsRequest): GetDestinationOptionsResponse =
        gatedRead("get_destination_options", Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) {
            val isTv = request.media.seerrMediaType().isSeerrTv()
            GetDestinationOptionsResponse
                .newBuilder()
                .setDestination(connection.api().destinationOptions(isTv, request.serverId, allow4k = mayRequest4k()))
                .build()
        }

    /**
     * Requires CAPABILITY_ADVANCED_REQUEST_OPTIONS. An empty axis on the request falls back to
     * the integration's own default for it — the same one [getAdvancedRequestOptions] preselected
     * — so a submit that never touched a picker is a plain request in every way but its path.
     */
    override suspend fun submitAdvancedRequest(request: SubmitAdvancedRequestRequest): SubmitAdvancedRequestResponse =
        gated("submit_advanced_request", Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) {
            val media = request.media
            refuseIfKnownBlocklisted(media)
            val isTv = media.seerrMediaType().isSeerrTv()
            // 4K here is a property of the server the caller named. A user who may not request 4K is
            // never offered a 4K server, so naming one is INVALID_ARGUMENT, like any server not offered.
            val destination =
                connection.api().resolveAdvancedDestination(
                    isTv,
                    request.serverId,
                    request.profileId,
                    request.rootFolderId,
                    allow4k = mayRequest4k(),
                )
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
        reportingFailure("get_status") { GetStatusResponse.newBuilder().setStatus(status(request.media)).build() }

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
                emit(reportingFailure("observe_status") { status(request.media, allowCached = fromCache) })
                fromCache = false
                delay(observeIntervalMillis)
            }
        }.distinctUntilChanged()
            .map { ObserveStatusResponse.newBuilder().setStatus(it).build() }

    override suspend fun cancelRequest(request: CancelRequestRequest): CancelRequestResponse =
        gated("cancel_request", Capability.CAPABILITY_CANCEL) {
            connection.api().deleteRequest(request.requestId)
            requestCache.delete(request.requestId)
            CancelRequestResponse.getDefaultInstance()
        }

    override suspend fun approveRequest(request: ApproveRequestRequest): ApproveRequestResponse =
        gated("approve_request", Capability.CAPABILITY_APPROVE) {
            connection.api().approveRequest(request.requestId)
            requestCache.updateStatus(request.requestId, SeerrRequestStatusCode.Approved.raw)
            ApproveRequestResponse.getDefaultInstance()
        }

    override suspend fun declineRequest(request: DeclineRequestRequest): DeclineRequestResponse =
        gated("decline_request", Capability.CAPABILITY_DECLINE) {
            connection.api().declineRequest(request.requestId)
            requestCache.updateStatus(request.requestId, SeerrRequestStatusCode.Declined.raw)
            DeclineRequestResponse.getDefaultInstance()
        }

    override suspend fun retryRequest(request: RetryRequestRequest): RetryRequestResponse =
        gated("retry_request", Capability.CAPABILITY_RETRY) {
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
        gated("edit_request", Capability.CAPABILITY_EDIT_SEASONS) {
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
        reportingFailure("get_attention") { GetAttentionResponse.newBuilder().setAttention(attention()).build() }

    /** Polled on this app's own cadence, like [observeStatus]; a push only when the value changed. */
    override fun observeAttention(request: ObserveAttentionRequest): Flow<ObserveAttentionResponse> =
        flow {
            while (true) {
                emit(reportingFailure("observe_attention") { attention() })
                delay(attentionIntervalMillis)
            }
        }.distinctUntilChanged()
            .map { ObserveAttentionResponse.newBuilder().setAttention(it).build() }

    private suspend fun attention(): Attention = connection.readAttention()

    /**
     * The one operation that needs Seerr's own id space: the server's media record, not the TMDB id.
     *
     * An issue needs something to report against: the contract answers FAILED_PRECONDITION unless the title is
     * available or partially available, an unrequested one included (#682). The 4K version counts for a user
     * declared `CAPABILITY_REQUEST_4K` (#704). One details read gives both that and Seerr's own media record id.
     */
    override suspend fun reportIssue(request: ReportIssueRequest): ReportIssueResponse =
        gated("report_issue", Capability.CAPABILITY_REPORT_ISSUE) {
            val info = connection.api().details(request.media).mediaInfo
            val status = info.toRequestStatus(clock())
            val sees4k = mayRequest4k()
            if (!status.isReportable(sees4k = sees4k)) {
                // The 4K state is named only to a user who may see it, as withAllowedActions does for every status.
                val fourK = if (sees4k) " (4K: ${status.availability4K})" else ""
                throw StatusException(
                    Status.FAILED_PRECONDITION.withDescription(
                        "Nothing to report against: the title is ${status.availability}$fourK",
                    ),
                )
            }
            val mediaId = info.recordIdFor(request.media)
            connection.api().createIssue(SeerrCreateIssueBody(mediaId, request.type.toSeerrIssueType(), request.message))
            ReportIssueResponse.getDefaultInstance()
        }

    /**
     * A title the cache already knows is blocklisted is refused here, without asking Seerr: FAILED_PRECONDITION, as
     * the contract says (#682). A cold cache falls through to Seerr, whose own refusal maps the same way.
     */
    private suspend fun refuseIfKnownBlocklisted(media: MediaId) {
        if (cachedStatus(media)?.status?.availability == Availability.AVAILABILITY_BLOCKLISTED) {
            throw StatusException(Status.FAILED_PRECONDITION.withDescription("The title is blocklisted"))
        }
    }

    override suspend fun blockTitle(request: BlockTitleRequest): BlockTitleResponse =
        gated("block_title", Capability.CAPABILITY_BLOCK) {
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
        gated("unblock_title", Capability.CAPABILITY_BLOCK) {
            connection.api().removeFromBlocklist(
                connection.profile().blocklistPath,
                request.media.tmdbId,
                connection.profile().unblockMediaType(request.media.seerrMediaType()),
            )
            UnblockTitleResponse.getDefaultInstance()
        }

    /**
     * Requires CAPABILITY_LIST_REQUESTS. One page of Seerr's `GET /request`, filtered as
     * [SeerrRequestQuery] describes and paged by [ListRequestsPageToken]. Each entry's status is the
     * title's, read as [getStatus] reads it, so a title's row in the status cache serves both. A title
     * listed twice on a page (a standard and a 4K request) is looked up once.
     *
     * AWAITING_MODERATION is empty for a user who may not moderate, without asking Seerr: the server
     * would answer with that user's own pending requests, which are not waiting on them.
     */
    override suspend fun listRequests(request: ListRequestsRequest): ListRequestsResponse =
        gatedRead("list_requests", Capability.CAPABILITY_LIST_REQUESTS) {
            val query = request.filter.toSeerrQuery()
            val pageSize = request.effectivePageSize()
            val skip = ListRequestsPageToken.skipOf(request)
            val user = connection.authenticatedUser()
            val permissions = user.toPermissions()
            if (query == SeerrRequestQuery.AwaitingModeration && !permissions.canManageRequests) {
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
                val statuses = statusesOf(listed.map { it.first })
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
     * Requires CAPABILITY_BATCH_STATUS. [getStatus] for each title, through the same cache. Every
     * title is checked before any is looked up, so an unusable one fails the call without a request
     * to Seerr. A title named twice is looked up once and answered twice, as the contract says.
     */
    override suspend fun getStatuses(request: GetStatusesRequest): GetStatusesResponse =
        gatedRead("get_statuses", Capability.CAPABILITY_BATCH_STATUS) {
            if (request.mediaCount > MAX_STATUSES_PER_CALL) {
                throw invalidArgument("at most $MAX_STATUSES_PER_CALL titles per call, was ${request.mediaCount}")
            }
            request.mediaList.forEach { it.seerrMediaType() }
            val statuses = statusesOf(request.mediaList)
            val response = GetStatusesResponse.newBuilder()
            request.mediaList.forEach { media ->
                response.addStatuses(
                    TitleStatus
                        .newBuilder()
                        .setMedia(media)
                        .setStatus(statuses.getValue(media)),
                )
            }
            response.build()
        }

    /**
     * Each distinct title's status, looked up side by side. At most [MAX_PARALLEL_LOOKUPS] go to the
     * server at once, so a cold page does not open fifty connections to it.
     */
    private suspend fun statusesOf(media: List<MediaId>): Map<MediaId, RequestStatus> {
        val lookups = Semaphore(MAX_PARALLEL_LOOKUPS)
        return coroutineScope {
            media
                .distinct()
                .map { title -> async { title to lookups.withPermit { status(title) } } }
                .awaitAll()
                .toMap()
        }
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
        val fetched = CachedStatus(info.toRequestStatus(now), now, info.requesterIds(), info.standardDownload(now))
        if (freshness.maxAgeMillis(fetched.status) != null) statusCache.put(media, fetched)
        return fetched
    }

    private suspend fun <T> gated(
        operation: String,
        capability: Capability,
        block: suspend () -> T,
    ): T =
        reportingFailure(operation) {
            checkDeclared(capability)
            // Every gated rpc is a write, and a write to any title makes every cached row suspect —
            // most of them name a request id rather than a title, so there is nothing narrower to drop.
            block().also { statusCache.clearAll() }
        }

    /** As [gated], for an rpc that only reads: no cache to invalidate behind it. */
    private suspend fun <T> gatedRead(
        operation: String,
        capability: Capability,
        block: suspend () -> T,
    ): T =
        reportingFailure(operation) {
            checkDeclared(capability)
            block()
        }

    /**
     * [statusCatching], reporting a failure a server version could explain (#539). The failure is
     * mapped to its [StatusException] first and that is what is thrown, so what is reported and what
     * the host receives are the same classification.
     *
     * An `observe*` poll goes through it per tick, and a failure ends the stream, so a failing server
     * is reported once per stream rather than once per tick.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> reportingFailure(
        operation: String,
        block: suspend () -> T,
    ): T =
        statusCatching {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analytics.operationFailed(operation, e, connection)
                throw e.toStatusException()
            }
        }

    private suspend fun declared(): Set<Capability> = permissions().toCapabilities(connection.profile())

    /** For a gated rpc only. A flag on a request is a bad argument, not a refused rpc: see [mayRequest4k]. */
    private suspend fun checkDeclared(capability: Capability) {
        declared().requireDeclared(capability)
    }

    /** Whether CAPABILITY_REQUEST_4K is declared, which decides `is_4k` on a submit and the 4K servers on the advanced path. */
    private suspend fun mayRequest4k(): Boolean = Capability.CAPABILITY_REQUEST_4K in declared()

    private companion object {
        const val OBSERVE_INTERVAL_MILLIS = 15_000L

        /** How many title lookups [statusesOf] sends to the server at once. */
        const val MAX_PARALLEL_LOOKUPS = 4

        /** Coarser than a title's status: the host holds this stream open for as long as it runs. */
        const val ATTENTION_INTERVAL_MILLIS = 60_000L
    }
}

/** What a handshake declares with no working session: the attention read, the one rpc that reports it. */
private val NO_SESSION_CAPABILITIES = setOf(Capability.CAPABILITY_ATTENTION)
