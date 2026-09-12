package io.github.scottcooper92.binge.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.integration.contracts.request.v1.ApproveRequestRequest
import com.binge.integration.contracts.request.v1.Availability
import com.binge.integration.contracts.request.v1.BlockTitleRequest
import com.binge.integration.contracts.request.v1.CancelRequestRequest
import com.binge.integration.contracts.request.v1.Capability
import com.binge.integration.contracts.request.v1.GetStatusRequest
import com.binge.integration.contracts.request.v1.HandshakeRequest
import com.binge.integration.contracts.request.v1.IssueType
import com.binge.integration.contracts.request.v1.ObserveStatusRequest
import com.binge.integration.contracts.request.v1.ReportIssueRequest
import com.binge.integration.contracts.request.v1.RequestServiceGrpcKt
import com.binge.integration.contracts.request.v1.SubmitRequestRequest
import com.binge.integration.contracts.v1.MediaId
import com.binge.integration.contracts.v1.MediaType
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ADMIN = 2
private const val REQUEST = 1 shl 5
private const val CREATE_ISSUES = 1 shl 22

/**
 * The whole contract end to end, on the JVM: a host's generated stub over an in-process channel
 * into this service, and this service over real HTTP into a scripted Seerr. What it pins is the
 * translation — permissions to capabilities, Seerr's ids and codes to the contract's — and that
 * the gate on a capability is enforced here, not trusted to the host.
 */
class SeerrRequestServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer().apply { start() }
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private val movie: MediaId =
        MediaId
            .newBuilder()
            .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
            .setTmdbId(603)
            .build()

    private fun connected(permissions: Int = ADMIN): RequestServiceGrpcKt.RequestServiceCoroutineStub {
        val store =
            CredentialStore(
                dataStore = PreferenceDataStoreFactory.create { folder.newFile("creds.preferences_pb") },
                cipher = PlainCipher,
            )
        runBlocking { store.save(SeerrCredentials(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"), SeerrVariant.Jellyseerr)) }
        val connection = SeerrConnection(store, SeerrApiFactory(logRequests = false))
        seerr.enqueue(json("""{"id":1,"permissions":$permissions}"""))
        val stub = serve(SeerrRequestService(connection, versionName = "0.1.0-test", clock = { 0L }, observeIntervalMillis = 1))
        // One handshake up front consumes the `auth/me` answer and caches the user, so each test's
        // recorded requests are its own rather than starting with the permission lookup.
        runBlocking { stub.handshake(HandshakeRequest.getDefaultInstance()) }
        seerr.takeRequest()
        return stub
    }

    private fun serve(service: SeerrRequestService): RequestServiceGrpcKt.RequestServiceCoroutineStub {
        val name = InProcessServerBuilder.generateName()
        server =
            InProcessServerBuilder
                .forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        return RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)
    }

    @After
    fun tearDown() {
        if (::channel.isInitialized) channel.shutdownNow()
        if (::server.isInitialized) server.shutdownNow()
        seerr.close()
    }

    @Test
    fun `handshake declares what the signed-in user may do, under the detected fork's name`() =
        runTest {
            val stub = connected(permissions = REQUEST or CREATE_ISSUES)

            val response = stub.handshake(HandshakeRequest.getDefaultInstance())

            assertEquals("Jellyseerr", response.providerName)
            assertEquals(
                setOf(Capability.CAPABILITY_OBSERVE_STATUS, Capability.CAPABILITY_CANCEL, Capability.CAPABILITY_REPORT_ISSUE),
                response.capabilitiesList.toSet(),
            )
        }

    @Test
    fun `an admin declares everything`() =
        runTest {
            val response = connected(permissions = ADMIN).handshake(HandshakeRequest.getDefaultInstance())

            assertEquals(
                Capability.entries.toSet() - Capability.UNRECOGNIZED - Capability.CAPABILITY_UNSPECIFIED,
                response.capabilitiesList.toSet(),
            )
        }

    @Test
    fun `status translates the title and narrows the allowed actions to its state`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":5,"requests":[{"id":4,"status":2}]}}"""))

            val status = stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie).build()).status

            assertEquals("/api/v1/movie/603", seerr.takeRequest().url.encodedPath)
            assertEquals(Availability.AVAILABILITY_AVAILABLE, status.availability)
            assertTrue(Capability.CAPABILITY_REPORT_ISSUE in status.allowedActionsList)
            assertTrue(Capability.CAPABILITY_CANCEL in status.allowedActionsList)
            assertFalse("nothing is pending, so nothing is approvable", Capability.CAPABILITY_APPROVE in status.allowedActionsList)
        }

    @Test
    fun `submit reads the three outcomes off the status code`() =
        runTest {
            val stub = connected()
            val request =
                SubmitRequestRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setIs4K(true)
                    .build()

            seerr.enqueue(json("""{"id":77}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            val created = stub.submitRequest(request)
            assertEquals(77, created.requestId)
            assertFalse(created.alreadyRequested)
            assertEquals(Availability.AVAILABILITY_PENDING, created.status.availability)
            val posted = seerr.takeRequest()
            assertEquals("/api/v1/request", posted.url.encodedPath)
            assertTrue(
                posted.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"is4k\":true"),
            )
            assertTrue(
                posted.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"mediaType\":\"movie\""),
            )

            seerr.enqueue(MockResponse(code = 409, body = """{"message":"Request already exists"}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            assertTrue(stub.submitRequest(request).alreadyRequested)

            seerr.enqueue(MockResponse(code = 202, body = """{"message":"No seasons available to request"}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":5}}"""))
            val nothing = stub.submitRequest(request)
            assertEquals(0, nothing.requestId)
            assertFalse(nothing.alreadyRequested)
        }

    @Test
    fun `seerr's failures leave as the contract's status codes`() =
        runTest {
            val stub = connected()

            seerr.enqueue(MockResponse(code = 401))
            assertEquals(Status.Code.UNAUTHENTICATED, stub.status(movie))

            seerr.enqueue(MockResponse(code = 403, body = """{"message":"Movie Quota exceeded"}"""))
            assertEquals(Status.Code.RESOURCE_EXHAUSTED, stub.submit(movie))

            seerr.enqueue(MockResponse(code = 503))
            assertEquals(Status.Code.UNAVAILABLE, stub.status(movie))
        }

    @Test
    fun `nothing connected is UNAUTHENTICATED everywhere`() =
        runTest {
            val store = CredentialStore(PreferenceDataStoreFactory.create { folder.newFile("empty.preferences_pb") }, PlainCipher)
            val stub = serve(SeerrRequestService(SeerrConnection(store, SeerrApiFactory(logRequests = false)), "0.1.0-test"))

            assertEquals(Status.Code.UNAUTHENTICATED, stub.code { handshake(HandshakeRequest.getDefaultInstance()) })
            assertEquals(Status.Code.UNAUTHENTICATED, stub.status(movie))
            assertEquals(Status.Code.UNAUTHENTICATED, stub.submit(movie))
        }

    @Test
    fun `a gated rpc is refused for a user without the permission, before any request`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            val before = seerr.requestCount

            assertEquals(
                Status.Code.PERMISSION_DENIED,
                stub.code { approveRequest(ApproveRequestRequest.newBuilder().setRequestId(4).build()) },
            )
            assertEquals(
                Status.Code.PERMISSION_DENIED,
                stub.code {
                    blockTitle(
                        BlockTitleRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setTitle("The Matrix")
                            .build(),
                    )
                },
            )

            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `a report is filed against seerr's own media record, resolved from the tmdb id`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":5}}"""))
            seerr.enqueue(MockResponse(code = 201))

            stub.reportIssue(
                ReportIssueRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setType(IssueType.ISSUE_TYPE_AUDIO)
                    .setMessage("no sound")
                    .build(),
            )

            assertEquals("/api/v1/movie/603", seerr.takeRequest().url.encodedPath)
            val issue = seerr.takeRequest()
            assertEquals("/api/v1/issue", issue.url.encodedPath)
            assertTrue(
                issue.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"mediaId\":9"),
            )
            assertTrue(
                issue.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"issueType\":2"),
            )
        }

    @Test
    fun `a report against an untracked title is NOT_FOUND`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json("""{}"""))

            val code =
                stub.code {
                    reportIssue(
                        ReportIssueRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setType(IssueType.ISSUE_TYPE_VIDEO)
                            .build(),
                    )
                }

            assertEquals(Status.Code.NOT_FOUND, code)
        }

    @Test
    fun `cancel deletes the request`() =
        runTest {
            val stub = connected()
            seerr.enqueue(MockResponse(code = 204))

            stub.cancelRequest(CancelRequestRequest.newBuilder().setRequestId(4).build())

            val deleted = seerr.takeRequest()
            assertEquals("DELETE", deleted.method)
            assertEquals("/api/v1/request/4", deleted.url.encodedPath)
        }

    @Test
    fun `observe pushes a status only when it changes`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":3}}"""))

            val seen = stub.observeStatus(ObserveStatusRequest.newBuilder().setMedia(movie).build()).take(2).toList()

            assertEquals(
                listOf(Availability.AVAILABILITY_PENDING, Availability.AVAILABILITY_PROCESSING),
                seen.map { it.status.availability },
            )
        }

    private suspend fun RequestServiceGrpcKt.RequestServiceCoroutineStub.status(media: MediaId): Status.Code =
        code { getStatus(GetStatusRequest.newBuilder().setMedia(media).build()) }

    private suspend fun RequestServiceGrpcKt.RequestServiceCoroutineStub.submit(media: MediaId): Status.Code =
        code { submitRequest(SubmitRequestRequest.newBuilder().setMedia(media).build()) }

    private suspend fun <T> RequestServiceGrpcKt.RequestServiceCoroutineStub.code(
        call: suspend RequestServiceGrpcKt.RequestServiceCoroutineStub.() -> T,
    ): Status.Code =
        try {
            call()
            Status.Code.OK
        } catch (e: StatusException) {
            e.status.code
        }

    private fun json(body: String): MockResponse =
        MockResponse(code = 200, headers = okhttp3.Headers.headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
