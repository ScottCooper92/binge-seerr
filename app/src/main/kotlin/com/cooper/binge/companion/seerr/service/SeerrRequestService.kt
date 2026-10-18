package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.ApproveRequestRequest
import com.binge.companion.contracts.request.v1.ApproveRequestResponse
import com.binge.companion.contracts.request.v1.Attention
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
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.RetryRequestRequest
import com.binge.companion.contracts.request.v1.RetryRequestResponse
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestRequest
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestResponse
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestResponse
import com.binge.companion.contracts.request.v1.UnblockTitleRequest
import com.binge.companion.contracts.request.v1.UnblockTitleResponse
import com.binge.companion.sdk.handshakeResponse
import com.binge.companion.sdk.requireDeclared
import com.binge.companion.sdk.withUserMessage
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.NoBingeConnectionStore
import com.cooper.binge.companion.seerr.auth.NotConnectedException
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.data.NoMediaStatusStore
import com.cooper.binge.companion.seerr.data.NoRequestStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.rejectsSession
import com.cooper.binge.companion.seerr.seerr.statusCatching
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toStatusException
import com.cooper.binge.companion.seerr.seerr.userFacingRefusal
import com.cooper.binge.companion.seerr.seerr.versionsFor
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.NoOpAnalytics
import com.cooper.binge.companion.seerr.telemetry.operationFailed
import io.grpc.StatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

/**
 * REQUEST v1, served against the connected Seerr server.
 *
 * Capabilities are the user's permissions narrowed by `SeerrServerProfile`. Failures leave as gRPC codes
 * (`SeerrErrors.kt`), and every gated rpc re-checks its capability rather than trusting the handshake.
 * The function count is suppressed, not reduced: REQUEST v1 has one rpc per function.
 *
 * This class holds the handshake, the gate and the streams; the bodies live in [SeerrRequestRpcs],
 * [SeerrListingRpcs], [SeerrIssueRpcs] and the [SeerrStatusReads] they share.
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
    private val userMessages: UserMessages = UserMessages.None,
) : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
    private val freshness = MediaStatusFreshness(observeIntervalMillis)
    private val statusReads = SeerrStatusReads(connection, statusCache, freshness, clock)
    private val requests = SeerrRequestRpcs(connection, statusReads, statusCache, requestCache)
    private val listing = SeerrListingRpcs(connection, statusReads)
    private val issues = SeerrIssueRpcs(connection, clock)

    /**
     * Decides the capability set, so it re-reads the profile and user rather than the cached copies: a
     * host that rebinds would otherwise get what the user could do when the process started.
     *
     * Never UNAUTHENTICATED (binge-companions#106): the host gates every other call on these capabilities.
     * With no session it answers OK with only [CAPABILITY_ATTENTION][Capability.CAPABILITY_ATTENTION], which
     * is how the host reaches `needs_reconnect`, or the UNAUTHENTICATED that sends the user here to connect.
     * Any other failure, an unreachable server say, keeps its own code.
     */
    override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
        reportingFailure("handshake") {
            val profile =
                try {
                    connection.refreshProfile()
                } catch (_: NotConnectedException) {
                    null
                }
            val session = profile?.let { sessionOffer(it) } ?: SessionOffer.NONE
            val response =
                handshakeResponse(
                    capabilities = session.capabilities,
                    versions = versionsFor(session.offers4k),
                    providerName = (profile?.variant ?: SeerrVariant.Unknown).displayName,
                    companionVersionName = versionName,
                )
            bingeConnection.recordHandshake()
            response
        }

    private suspend fun sessionOffer(profile: SeerrServerProfile): SessionOffer =
        try {
            val permissions = connection.refreshAuthenticatedUser().toPermissions()
            SessionOffer(permissions.toCapabilities(profile), permissions.canRequest4kOn(profile.settings))
        } catch (e: HttpException) {
            if (!e.toSeerrError().rejectsSession) throw e
            SessionOffer.NONE
        }

    override suspend fun submitRequest(request: SubmitRequestRequest): SubmitRequestResponse =
        reportingFailure("submit_request") { requests.submitRequest(request) }

    override suspend fun getAdvancedRequestOptions(request: GetAdvancedRequestOptionsRequest): GetAdvancedRequestOptionsResponse =
        gatedRead("get_advanced_request_options", Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) {
            requests.getAdvancedRequestOptions(request)
        }

    override suspend fun getDestinationOptions(request: GetDestinationOptionsRequest): GetDestinationOptionsResponse =
        gatedRead("get_destination_options", Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) { requests.getDestinationOptions(request) }

    override suspend fun submitAdvancedRequest(request: SubmitAdvancedRequestRequest): SubmitAdvancedRequestResponse =
        gated("submit_advanced_request", Capability.CAPABILITY_ADVANCED_REQUEST_OPTIONS) { requests.submitAdvancedRequest(request) }

    override suspend fun getStatus(request: GetStatusRequest): GetStatusResponse =
        reportingFailure("get_status") { GetStatusResponse.newBuilder().setStatus(statusReads.status(request.media)).build() }

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
                emit(reportingFailure("observe_status") { statusReads.status(request.media, allowCached = fromCache) })
                fromCache = false
                delay(observeIntervalMillis)
            }
        }.distinctUntilChanged()
            .map { ObserveStatusResponse.newBuilder().setStatus(it).build() }

    override suspend fun cancelRequest(request: CancelRequestRequest): CancelRequestResponse =
        gated("cancel_request", Capability.CAPABILITY_CANCEL) { requests.cancelRequest(request) }

    override suspend fun approveRequest(request: ApproveRequestRequest): ApproveRequestResponse =
        gated("approve_request", Capability.CAPABILITY_APPROVE) { requests.approveRequest(request) }

    override suspend fun declineRequest(request: DeclineRequestRequest): DeclineRequestResponse =
        gated("decline_request", Capability.CAPABILITY_DECLINE) { requests.declineRequest(request) }

    override suspend fun retryRequest(request: RetryRequestRequest): RetryRequestResponse =
        gated("retry_request", Capability.CAPABILITY_RETRY) { requests.retryRequest(request) }

    override suspend fun editRequest(request: EditRequestRequest): EditRequestResponse =
        gated("edit_request", Capability.CAPABILITY_EDIT_SEASONS) { requests.editRequest(request) }

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

    override suspend fun reportIssue(request: ReportIssueRequest): ReportIssueResponse =
        gated("report_issue", Capability.CAPABILITY_REPORT_ISSUE) { issues.reportIssue(request) }

    override suspend fun blockTitle(request: BlockTitleRequest): BlockTitleResponse =
        gated("block_title", Capability.CAPABILITY_BLOCK) { issues.blockTitle(request) }

    override suspend fun unblockTitle(request: UnblockTitleRequest): UnblockTitleResponse =
        gated("unblock_title", Capability.CAPABILITY_BLOCK) { issues.unblockTitle(request) }

    override suspend fun listRequests(request: ListRequestsRequest): ListRequestsResponse =
        gatedRead("list_requests", Capability.CAPABILITY_LIST_REQUESTS) { listing.listRequests(request) }

    override suspend fun getStatuses(request: GetStatusesRequest): GetStatusesResponse =
        gatedRead("get_statuses", Capability.CAPABILITY_BATCH_STATUS) { listing.getStatuses(request) }

    private suspend fun permissions(): SeerrPermissions = connection.authenticatedUser().toPermissions()

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
     * [statusCatching], reporting a failure a server version could explain (#539). The failure is mapped
     * to its [StatusException] first, so what is reported and what the host receives match.
     * A failure ends an `observe*` stream, so a failing server is reported once per stream, not per tick.
     *
     * A refusal the user can act on (a spent quota, a blocklisted title) also carries a sentence for
     * them, in the contract's rich error model (binge-companions#132).
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
                throw e.toStatusException().withSentenceFor(e)
            }
        }

    private fun StatusException.withSentenceFor(cause: Throwable): StatusException {
        val refusal = cause.userFacingRefusal(status.code) ?: return this
        val sentence = userMessages.sentence(refusal) ?: return this
        return status.withUserMessage(refusal.reason, sentence.message, sentence.locale)
    }

    private suspend fun declared(): Set<Capability> = permissions().toCapabilities(connection.profile())

    /** For a gated rpc only. A flag on a request is a bad argument, not a refused rpc: see [SeerrConnection.mayRequest4k]. */
    private suspend fun checkDeclared(capability: Capability) {
        declared().requireDeclared(capability)
    }

    private companion object {
        const val OBSERVE_INTERVAL_MILLIS = 15_000L

        /** Coarser than a title's status: the host holds this stream open for as long as it runs. */
        const val ATTENTION_INTERVAL_MILLIS = 60_000L
    }
}

/** What a handshake declares: the capability set, and whether the 4K version is one the user may request. */
private data class SessionOffer(
    val capabilities: Set<Capability>,
    val offers4k: Boolean,
) {
    companion object {
        /** With no working session: the attention read, the one rpc that reports it, and no version beyond the standard one. */
        val NONE = SessionOffer(setOf(Capability.CAPABILITY_ATTENTION), offers4k = false)
    }
}
