package com.cooper.binge.companion.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.request.v1.DeleteFilesRequest
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.binge.companion.seerr.util.enqueueProfile
import com.cooper.binge.companion.seerr.util.routeProfiles
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ADMIN_USER = """{"id":1,"permissions":2}"""
private const val REQUESTER_USER = """{"id":1,"permissions":32}"""
private const val SETTINGS_4K = """{"initialized":true,"movie4kEnabled":true}"""

/** Available in the standard version, with a request in each state and one in 4K. */
private const val AVAILABLE_WITH_REQUESTS =
    """{"mediaInfo":{"id":9,"status":5,"status4k":1,"requests":[""" +
        """{"id":41,"status":2,"is4k":false},{"id":42,"status":1,"is4k":false},{"id":43,"status":3,"is4k":false},""" +
        """{"id":44,"status":2,"is4k":false},{"id":45,"status":2,"is4k":true}]}}"""

/** Requested and approved, nothing downloaded yet: Seerr's status for the title is still Pending. */
private const val REQUESTED_NO_FILES = """{"mediaInfo":{"id":9,"status":2,"requests":[{"id":41,"status":2,"is4k":false}]}}"""

private const val AVAILABLE_IN_4K = """{"mediaInfo":{"id":9,"status":1,"status4k":5,"requests":[{"id":45,"status":2,"is4k":true}]}}"""

private const val NOTHING_LEFT = """{"mediaInfo":{"id":9,"status":1,"requests":[]}}"""

/**
 * `DeleteFiles` (#1914) end to end: a host's stub over an in-process channel into this service, and this service over
 * real HTTP into a scripted Seerr. Request statuses are Seerr's codes: 1 pending, 2 approved, 3 declined, 4 failed, 5 completed.
 */
class SeerrDeleteFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer().routeProfiles().apply { start() }
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private val movie: MediaId =
        MediaId
            .newBuilder()
            .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
            .setTmdbId(603)
            .build()

    @After
    fun tearDown() {
        if (::channel.isInitialized) channel.shutdownNow()
        if (::server.isInitialized) server.shutdownNow()
        seerr.close()
    }

    @Test
    fun `it deletes the version's files, then withdraws its open requests and no others`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(AVAILABLE_WITH_REQUESTS))
            seerr.enqueue(answer(204))
            repeat(3) { seerr.enqueue(answer(204)) }

            stub.deleteFiles(deleteFiles())

            assertEquals(
                listOf(
                    "GET /api/v1/movie/603",
                    "DELETE /api/v1/media/9/file?is4k=false",
                    "DELETE /api/v1/request/41",
                    "DELETE /api/v1/request/42",
                    "DELETE /api/v1/request/44",
                ),
                taken(5),
            )
        }

    @Test
    fun `4K deletes the 4K files and withdraws only the 4K requests`() =
        runTest {
            val stub = connected(settings = SETTINGS_4K)
            seerr.enqueue(json(AVAILABLE_IN_4K))
            repeat(2) { seerr.enqueue(answer(204)) }

            stub.deleteFiles(deleteFiles("4k"))

            assertEquals(
                listOf("GET /api/v1/movie/603", "DELETE /api/v1/media/9/file?is4k=true", "DELETE /api/v1/request/45"),
                taken(3),
            )
        }

    @Test
    fun `a version with a request and no files is withdrawn without a file call, and is not gone`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(REQUESTED_NO_FILES))
            seerr.enqueue(answer(204))

            stub.deleteFiles(deleteFiles())

            assertEquals(listOf("GET /api/v1/movie/603", "DELETE /api/v1/request/41"), taken(2))
        }

    @Test
    fun `a title with neither files nor a request left is NOT_FOUND`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(NOTHING_LEFT))

            assertEquals(Status.Code.NOT_FOUND, stub.failure().status.code)
        }

    @Test
    fun `a title Seerr has no record of is NOT_FOUND`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json("{}"))

            assertEquals(Status.Code.NOT_FOUND, stub.failure().status.code)
        }

    @Test
    fun `a version the handshake did not list is INVALID_ARGUMENT before any call to Seerr`() =
        runTest {
            val stub = connected()

            assertEquals(Status.Code.INVALID_ARGUMENT, stub.failure(deleteFiles("8k")).status.code)
            assertEquals(Status.Code.INVALID_ARGUMENT, stub.failure(deleteFiles("4k")).status.code)
            assertEquals(0, seerr.requestCount - WARM_UP_CALLS)
        }

    @Test
    fun `an unspecified media is INVALID_ARGUMENT`() =
        runTest {
            val stub = connected()

            val failure = stub.failure(DeleteFilesRequest.getDefaultInstance())

            assertEquals(Status.Code.INVALID_ARGUMENT, failure.status.code)
        }

    @Test
    fun `a server with no default Radarr or Sonarr is FAILED_PRECONDITION, and no request is withdrawn`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(AVAILABLE_WITH_REQUESTS))
            seerr.enqueue(
                MockResponse(
                    code = 409,
                    headers = Headers.headersOf("Content-Type", "application/json"),
                    body = """{"message":"No Radarr server configured to delete media files"}""",
                ),
            )

            assertEquals(Status.Code.FAILED_PRECONDITION, stub.failure().status.code)
            assertEquals(listOf("GET /api/v1/movie/603", "DELETE /api/v1/media/9/file?is4k=false"), taken(2))
            assertEquals(WARM_UP_CALLS + 2, seerr.requestCount)
        }

    @Test
    fun `a refusal by the server is PERMISSION_DENIED`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(AVAILABLE_WITH_REQUESTS))
            seerr.enqueue(answer(403))
            seerr.enqueue(json(ADMIN_USER))

            assertEquals(Status.Code.PERMISSION_DENIED, stub.failure().status.code)
        }

    @Test
    fun `once the files are gone a failed withdrawal still answers OK`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(AVAILABLE_WITH_REQUESTS))
            seerr.enqueue(answer(204))
            seerr.enqueue(answer(404))
            repeat(2) { seerr.enqueue(answer(204)) }

            stub.deleteFiles(deleteFiles())

            assertEquals(WARM_UP_CALLS + 5, seerr.requestCount)
        }

    @Test
    fun `a user who may not manage requests is refused before any call to Seerr`() =
        runTest {
            val stub = connected(user = REQUESTER_USER)

            assertEquals(Status.Code.PERMISSION_DENIED, stub.failure().status.code)
            assertEquals(0, seerr.requestCount - WARM_UP_CALLS)
        }

    private fun deleteFiles(versionId: String = ""): DeleteFilesRequest =
        DeleteFilesRequest
            .newBuilder()
            .setMedia(movie)
            .setVersionId(versionId)
            .build()

    private suspend fun RequestServiceGrpcKt.RequestServiceCoroutineStub.failure(
        request: DeleteFilesRequest = deleteFiles(),
    ): StatusException =
        try {
            deleteFiles(request)
            error("the call should have failed")
        } catch (e: StatusException) {
            e
        }

    /** The next [count] requests Seerr saw, as method, path and query. */
    private fun taken(count: Int): List<String> =
        List(count) {
            seerr.takeRequest().let {
                "${it.method} ${it.url.encodedPath}${it.url.query?.let { q ->
                    "?$q"
                }.orEmpty()}"
            }
        }

    private fun answer(code: Int): MockResponse =
        MockResponse(
            code = code,
            headers = Headers.headersOf("Content-Type", "application/json"),
            body =
                if (code ==
                    204
                ) {
                    ""
                } else {
                    "{}"
                },
        )

    private fun json(body: String): MockResponse =
        MockResponse(code = 200, headers = Headers.headersOf("Content-Type", "application/json"), body = body)

    /** Signed in against the scripted server with the profile and user read once, so a later call needs only its own answers. */
    private fun connected(
        user: String = ADMIN_USER,
        settings: String = """{"initialized":true}""",
    ): RequestServiceGrpcKt.RequestServiceCoroutineStub {
        val store =
            CredentialStore(
                dataStore = PreferenceDataStoreFactory.create { folder.newFile("creds.preferences_pb") },
                cipher = PlainCipher,
                io = Dispatchers.Unconfined,
            )
        runBlocking { store.save(SeerrCredentials(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"), SeerrVariant.fromVersion("2.7.0"))) }
        val connection = SeerrConnection(store, SeerrApiFactory(logRequests = false))
        val name = InProcessServerBuilder.generateName()
        server =
            InProcessServerBuilder
                .forName(name)
                .directExecutor()
                .addService(SeerrRequestService(connection, versionName = "0.1.0-test"))
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        val stub = RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)
        seerr.enqueueProfile(json("""{"version":"2.7.0"}"""), json(settings))
        seerr.enqueue(json(user))
        runBlocking { stub.handshake(HandshakeRequest.getDefaultInstance()) }
        repeat(WARM_UP_CALLS) { seerr.takeRequest() }
        assertTrue(seerr.requestCount >= WARM_UP_CALLS)
        return stub
    }

    private companion object {
        /** The handshake's three reads: status, public settings and the signed-in user. */
        const val WARM_UP_CALLS = 3
    }
}
