package io.github.scottcooper92.binge.seerr.service

import com.binge.integration.contracts.request.v1.GetStatusRequest
import com.binge.integration.contracts.request.v1.GetStatusResponse
import com.binge.integration.contracts.request.v1.HandshakeRequest
import com.binge.integration.contracts.request.v1.HandshakeResponse
import com.binge.integration.contracts.request.v1.RequestServiceGrpcKt
import com.binge.integration.contracts.request.v1.SubmitRequestRequest
import com.binge.integration.contracts.request.v1.SubmitRequestResponse
import com.binge.integration.sdk.handshakeResponse
import io.grpc.Status
import io.grpc.StatusException

/**
 * REQUEST v1, served against a Seerr instance.
 *
 * Nothing is connected yet: this is the contract's mandatory core with no provider session behind
 * it, so a handshake declares no capabilities and every call answers `UNAUTHENTICATED` — the code
 * the contract reserves for "the integration's own session is broken, send the user to its app".
 * The Seerr client, the stored session and the eight operations arrive in the next changes.
 */
class SeerrRequestService(
    private val versionName: String,
) : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
    override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
        handshakeResponse(
            capabilities = emptySet(),
            providerName = PROVIDER_NAME,
            integrationVersionName = versionName,
        )

    override suspend fun submitRequest(request: SubmitRequestRequest): SubmitRequestResponse = throw notConnected()

    override suspend fun getStatus(request: GetStatusRequest): GetStatusResponse = throw notConnected()

    private fun notConnected(): StatusException =
        StatusException(Status.UNAUTHENTICATED.withDescription("No Seerr server is connected in the companion app"))

    private companion object {
        /** A brand name for the host's UI; the detected fork replaces it once a server is connected. */
        const val PROVIDER_NAME = "Seerr"
    }
}
