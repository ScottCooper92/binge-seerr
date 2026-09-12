package io.github.scottcooper92.binge.seerr.service

import com.binge.integration.contracts.request.v1.GetStatusRequest
import com.binge.integration.contracts.request.v1.HandshakeRequest
import com.binge.integration.contracts.request.v1.RequestServiceGrpcKt
import com.binge.integration.contracts.request.v1.SubmitRequestRequest
import com.binge.integration.contracts.v1.MediaId
import com.binge.integration.contracts.v1.MediaType
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The service driven over an in-process channel — the same generated stubs Binge's bridge speaks,
 * with no Binder and no device. What the transport adds is proven on hardware, not here.
 */
class SeerrRequestServiceTest {
    private val name = InProcessServerBuilder.generateName()
    private val server: Server =
        InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(SeerrRequestService(versionName = "0.1.0-test"))
            .build()
            .start()
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(name).directExecutor().build()
    private val stub = RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)

    @After
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `handshake names the provider and this build, and declares nothing while unconnected`() =
        runTest {
            val response = stub.handshake(HandshakeRequest.getDefaultInstance())

            assertEquals("Seerr", response.providerName)
            assertEquals("0.1.0-test", response.integrationVersionName)
            assertTrue(response.capabilitiesList.isEmpty())
        }

    @Test
    fun `the core rpcs answer UNAUTHENTICATED while no server is connected`() =
        runTest {
            val media =
                MediaId
                    .newBuilder()
                    .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
                    .setTmdbId(603)
                    .build()

            val submit = runCatching { stub.submitRequest(SubmitRequestRequest.newBuilder().setMedia(media).build()) }
            val status = runCatching { stub.getStatus(GetStatusRequest.newBuilder().setMedia(media).build()) }

            assertEquals(Status.Code.UNAUTHENTICATED, submit.statusCode())
            assertEquals(Status.Code.UNAUTHENTICATED, status.statusCode())
        }

    private fun Result<*>.statusCode(): Status.Code? = (exceptionOrNull() as? StatusException)?.status?.code
}
