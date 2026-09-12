package io.github.scottcooper92.binge.seerr.service

import com.binge.integration.contracts.request.v1.ApprovalState
import com.binge.integration.contracts.request.v1.ApproveRequestRequest
import com.binge.integration.contracts.request.v1.ApproveRequestResponse
import com.binge.integration.contracts.request.v1.Availability
import com.binge.integration.contracts.request.v1.BlockTitleRequest
import com.binge.integration.contracts.request.v1.BlockTitleResponse
import com.binge.integration.contracts.request.v1.CancelRequestRequest
import com.binge.integration.contracts.request.v1.CancelRequestResponse
import com.binge.integration.contracts.request.v1.Capability
import com.binge.integration.contracts.request.v1.DeclineRequestRequest
import com.binge.integration.contracts.request.v1.DeclineRequestResponse
import com.binge.integration.contracts.request.v1.GetStatusRequest
import com.binge.integration.contracts.request.v1.GetStatusResponse
import com.binge.integration.contracts.request.v1.HandshakeRequest
import com.binge.integration.contracts.request.v1.HandshakeResponse
import com.binge.integration.contracts.request.v1.IssueType
import com.binge.integration.contracts.request.v1.ObserveStatusRequest
import com.binge.integration.contracts.request.v1.ObserveStatusResponse
import com.binge.integration.contracts.request.v1.ReportIssueRequest
import com.binge.integration.contracts.request.v1.ReportIssueResponse
import com.binge.integration.contracts.request.v1.RequestServiceGrpcKt
import com.binge.integration.contracts.request.v1.RequestStatus
import com.binge.integration.contracts.request.v1.RetryRequestRequest
import com.binge.integration.contracts.request.v1.RetryRequestResponse
import com.binge.integration.contracts.request.v1.SubmitRequestRequest
import com.binge.integration.contracts.request.v1.SubmitRequestResponse
import com.binge.integration.contracts.v1.MediaId
import com.binge.integration.sdk.handshakeResponse
import com.binge.integration.sdk.requireDeclared
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrAddToBlocklistBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrCreateIssueBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueTypeCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaIds
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestBody
import io.github.scottcooper92.binge.seerr.seerr.details
import io.github.scottcooper92.binge.seerr.seerr.seerrMediaType
import io.github.scottcooper92.binge.seerr.seerr.statusCatching
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toRequestStatus
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
 * Capabilities come from the signed-in user's permission bitmask, so the host offers exactly what
 * this user may do. Every failure leaves as a gRPC status code (`SeerrErrors.kt`), and every gated
 * rpc re-checks its capability rather than trusting the host to have honoured the handshake.
 */
class SeerrRequestService(
    private val connection: SeerrConnection,
    private val versionName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val observeIntervalMillis: Long = OBSERVE_INTERVAL_MILLIS,
) : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
    private val mediaIds = SeerrMediaIds { connection.api() }

    override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
        statusCatching {
            val credentials = connection.current()
            handshakeResponse(
                capabilities = permissions().toCapabilities(),
                providerName = credentials.variant.displayName,
                integrationVersionName = versionName,
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
            val api = connection.api()
            val response = api.requestMedia(body)
            val builder = SubmitRequestResponse.newBuilder()
            when {
                response.code() == HTTP_ACCEPTED -> Unit
                response.isSuccessful -> response.body()?.id?.let(builder::setRequestId)
                response.code() == HTTP_CONFLICT -> builder.setAlreadyRequested(true)
                else -> throw HttpException(response)
            }
            builder.setStatus(status(media)).build()
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
            while (true) {
                emit(statusCatching { status(request.media) })
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

    /** The one operation that needs Seerr's own id space: the server's media record, not the TMDB id. */
    override suspend fun reportIssue(request: ReportIssueRequest): ReportIssueResponse =
        gated(Capability.CAPABILITY_REPORT_ISSUE) {
            val mediaId = mediaIds.mediaRecordId(request.media)
            connection.api().createIssue(SeerrCreateIssueBody(mediaId, request.type.toSeerrIssueType().raw, request.message))
            ReportIssueResponse.getDefaultInstance()
        }

    override suspend fun blockTitle(request: BlockTitleRequest): BlockTitleResponse =
        gated(Capability.CAPABILITY_BLOCK) {
            connection.api().addToBlocklist(SeerrAddToBlocklistBody(request.media.tmdbId, request.media.seerrMediaType(), request.title))
            BlockTitleResponse.getDefaultInstance()
        }

    private suspend fun permissions(): SeerrPermissions = connection.authenticatedUser().toPermissions()

    private suspend fun status(media: MediaId): RequestStatus {
        val permissions = permissions()
        val status =
            connection
                .api()
                .details(media)
                .mediaInfo
                .toRequestStatus(clock())
        return status.toBuilder().addAllAllowedActions(permissions.allowedActions(status)).build()
    }

    private suspend fun <T> gated(
        capability: Capability,
        block: suspend () -> T,
    ): T =
        statusCatching {
            permissions().toCapabilities().requireDeclared(capability)
            block()
        }

    private companion object {
        const val OBSERVE_INTERVAL_MILLIS = 15_000L
    }
}

/** Seerr's permissions as the contract's capability set — the handshake is derived, never hand-listed. */
fun SeerrPermissions.toCapabilities(): Set<Capability> =
    buildSet {
        add(Capability.CAPABILITY_OBSERVE_STATUS)
        if (canRequest4k) add(Capability.CAPABILITY_REQUEST_4K)
        if (canRequestAdvanced) add(Capability.CAPABILITY_ADVANCED_OPTIONS)
        if (canRequest) add(Capability.CAPABILITY_CANCEL)
        if (canManageRequests) addAll(listOf(Capability.CAPABILITY_APPROVE, Capability.CAPABILITY_DECLINE, Capability.CAPABILITY_RETRY))
        if (canCreateIssues) add(Capability.CAPABILITY_REPORT_ISSUE)
        if (canManageBlocklist) add(Capability.CAPABILITY_BLOCK)
    }

/**
 * The declared capabilities that apply to THIS title right now: approve and decline only with a
 * pending request to decide, retry only with a failed one, a report only against something
 * available, a cancel only with something to cancel.
 */
fun SeerrPermissions.allowedActions(status: RequestStatus): List<Capability> {
    val declared = toCapabilities()
    val states = status.requestsList.map { it.state }
    val reportable =
        status.availability == Availability.AVAILABILITY_AVAILABLE ||
            status.availability == Availability.AVAILABILITY_PARTIALLY_AVAILABLE
    return declared.filter { capability ->
        when (capability) {
            Capability.CAPABILITY_APPROVE, Capability.CAPABILITY_DECLINE -> states.any { it.isPending() }
            Capability.CAPABILITY_RETRY -> states.any { it.isFailed() }
            Capability.CAPABILITY_CANCEL -> states.any { !it.isDeclined() }
            Capability.CAPABILITY_REPORT_ISSUE -> reportable
            Capability.CAPABILITY_BLOCK -> status.availability != Availability.AVAILABILITY_BLOCKLISTED
            else -> true
        }
    }
}

private fun ApprovalState.isPending() = this == ApprovalState.APPROVAL_STATE_PENDING

private fun ApprovalState.isFailed() = this == ApprovalState.APPROVAL_STATE_FAILED

private fun ApprovalState.isDeclined() = this == ApprovalState.APPROVAL_STATE_DECLINED

private fun IssueType.toSeerrIssueType(): SeerrIssueTypeCode =
    when (this) {
        IssueType.ISSUE_TYPE_VIDEO -> SeerrIssueTypeCode.Video
        IssueType.ISSUE_TYPE_AUDIO -> SeerrIssueTypeCode.Audio
        IssueType.ISSUE_TYPE_SUBTITLE -> SeerrIssueTypeCode.Subtitles
        IssueType.ISSUE_TYPE_OTHER, IssueType.ISSUE_TYPE_UNSPECIFIED, IssueType.UNRECOGNIZED -> SeerrIssueTypeCode.Other
    }
