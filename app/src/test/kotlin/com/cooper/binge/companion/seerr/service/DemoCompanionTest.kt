package com.cooper.binge.companion.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.request.v1.ApproveRequestRequest
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.GetStatusRequest
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.IssueType
import com.binge.companion.contracts.request.v1.ListRequestsRequest
import com.binge.companion.contracts.request.v1.ReportIssueRequest
import com.binge.companion.contracts.request.v1.RequestFilter
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.demo.DEMO_PROVIDER_NAME
import com.cooper.binge.companion.seerr.demo.demoCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.standardVersion
import com.cooper.binge.companion.seerr.util.PlainCipher
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SINTEL = 45745
private const val INCEPTION = 27205
private const val METROPOLIS = 19
private const val PAGE_SIZE = 20

/**
 * What Binge sees of the demo (#1705): the exported service's own code, over an in-process channel, on a connection to the
 * demo server, with no socket anywhere. A reviewer who connects Binge to the demo can look up, request, approve and report.
 */
class DemoCompanionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    @After
    fun tearDown() {
        if (::channel.isInitialized) channel.shutdownNow()
        if (::server.isInitialized) server.shutdownNow()
    }

    private fun movie(tmdbId: Int): MediaId =
        MediaId
            .newBuilder()
            .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
            .setTmdbId(tmdbId)
            .build()

    private suspend fun TestScope.demoStub(): RequestServiceGrpcKt.RequestServiceCoroutineStub {
        val store =
            CredentialStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                PlainCipher,
            )
        val connection = SeerrConnection(store, SeerrApiFactory(logRequests = false))
        demoCredentials().let { connection.connect(it.baseUrl, it.auth).getOrThrow() }
        val name = InProcessServerBuilder.generateName()
        server =
            InProcessServerBuilder
                .forName(name)
                .directExecutor()
                .addService(SeerrRequestService(connection, versionName = "0.1.0-test"))
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        return RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)
    }

    @Test
    fun `the handshake names the demo and offers an administrator's whole set`() =
        runTest {
            val response = demoStub().handshake(HandshakeRequest.getDefaultInstance())

            assertEquals(DEMO_PROVIDER_NAME, response.providerName)
            val offered = response.capabilitiesList.toSet()
            listOf(
                Capability.CAPABILITY_APPROVE,
                Capability.CAPABILITY_DECLINE,
                Capability.CAPABILITY_REPORT_ISSUE,
                Capability.CAPABILITY_LIST_REQUESTS,
                Capability.CAPABILITY_OBSERVE_STATUS,
            ).forEach { assertTrue("$it is not offered", it in offered) }
        }

    @Test
    fun `a title the demo has never heard of can be requested, and then reads as requested`() =
        runTest {
            val stub = demoStub()

            val created = stub.submitRequest(SubmitRequestRequest.newBuilder().setMedia(movie(INCEPTION)).build())
            val status = stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie(INCEPTION)).build()).status

            assertTrue(created.requestId.isNotEmpty())
            assertEquals(Availability.AVAILABILITY_PROCESSING, status.standardVersion().availability)
        }

    @Test
    fun `a pending request is approved through the contract, and a report on an available title is accepted`() =
        runTest {
            val stub = demoStub()
            val pending = stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie(SINTEL)).build()).status
            assertEquals(Availability.AVAILABILITY_PENDING, pending.standardVersion().availability)

            val requests =
                stub.listRequests(
                    ListRequestsRequest
                        .newBuilder()
                        .setFilter(RequestFilter.REQUEST_FILTER_ALL)
                        .setPageSize(PAGE_SIZE)
                        .build(),
                )
            val sintel = requests.entriesList.first { it.media.tmdbId == SINTEL }
            stub.approveRequest(ApproveRequestRequest.newBuilder().setRequestId(sintel.request.id).build())
            stub.reportIssue(
                ReportIssueRequest
                    .newBuilder()
                    .setMedia(movie(METROPOLIS))
                    .setType(IssueType.ISSUE_TYPE_VIDEO)
                    .setMessage("Blurry.")
                    .build(),
            )

            val after = stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie(SINTEL)).build()).status
            assertEquals(Availability.AVAILABILITY_PROCESSING, after.standardVersion().availability)
        }
}
