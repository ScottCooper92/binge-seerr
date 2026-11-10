package com.cooper.binge.companion.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.GetStatusRequest
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.ServerUnreachable
import com.binge.companion.sdk.serverUnreachableOf
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
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
import okhttp3.Interceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLHandshakeException

private const val PROFILE = """{"version":"2.7.0"}"""
private const val SETTINGS = """{"initialized":true}"""
private const val USER = """{"id":1,"permissions":2}"""

/**
 * `SERVER_UNREACHABLE` (#1913): a signed-in user whose server does not answer gets the detail, and nobody
 * gets it for an answer, however unwelcome. The server here is real HTTP, so a refused connection is one.
 */
class SeerrServerUnreachableTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer().routeProfiles().apply { start() }
    private val timingOut = AtomicBoolean(false)
    private val untrusted = AtomicBoolean(false)
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
    fun `a server that refuses connections is unreachable, named by its own title`() =
        runTest {
            val stub = connected(title = "Movie Night")
            seerr.close()

            val failure = stub.failure()

            assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
            assertEquals(ServerUnreachable("Movie Night"), serverUnreachableOf(failure))
        }

    @Test
    fun `a server that times out is a deadline exceeded, and still unreachable`() =
        runTest {
            val stub = connected(title = "Movie Night")
            timingOut.set(true)

            val failure = stub.failure()

            assertEquals(Status.Code.DEADLINE_EXCEEDED, failure.status.code)
            assertEquals(ServerUnreachable("Movie Night"), serverUnreachableOf(failure))
        }

    @Test
    fun `a certificate the device will not trust is retryable but not unreachable`() =
        runTest {
            val stub = connected()
            untrusted.set(true)

            assertAnswered(Status.Code.UNAVAILABLE, stub.failure())
        }

    @Test
    fun `a server behind a proxy that answers 503 is unreachable`() =
        runTest {
            val stub = connected()
            seerr.enqueue(MockResponse(code = 503))

            val failure = stub.failure()

            assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
            assertEquals(ServerUnreachable("Jellyseerr"), serverUnreachableOf(failure))
        }

    @Test
    fun `with no title the edition's name stands in`() =
        runTest {
            val stub = connected(title = null)
            seerr.close()

            assertEquals(ServerUnreachable("Jellyseerr"), serverUnreachableOf(stub.failure()))
        }

    @Test
    fun `a cold start still names the edition without asking the dead server for a profile`() =
        runTest {
            val stub = connected(warm = false)
            seerr.close()

            assertEquals(ServerUnreachable("Jellyseerr"), serverUnreachableOf(stub.failure()))
        }

    @Test
    fun `a handshake that cannot reach the server carries the detail`() =
        runTest {
            val stub = connected(warm = false)
            seerr.close()

            val failure = stub.failure { handshake(HandshakeRequest.getDefaultInstance()) }

            assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
            assertTrue(serverUnreachableOf(failure) != null)
        }

    @Test
    fun `a rejected session is not unreachable`() =
        runTest {
            val stub = connected()
            // The session check against auth/me is rejected too, so the session is what failed.
            repeat(2) { seerr.enqueue(answer(401)) }

            assertAnswered(Status.Code.UNAUTHENTICATED, stub.failure())
        }

    @Test
    fun `a refusal is not unreachable`() =
        runTest {
            val stub = connected()
            seerr.enqueue(answer(403))
            seerr.enqueue(json(USER))

            assertAnswered(Status.Code.PERMISSION_DENIED, stub.failure())
        }

    @Test
    fun `a missing title is not unreachable`() =
        runTest {
            val stub = connected()
            seerr.enqueue(answer(404))

            assertAnswered(Status.Code.NOT_FOUND, stub.failure())
        }

    @Test
    fun `a bad request is not unreachable`() =
        runTest {
            val stub = connected()
            seerr.enqueue(answer(400))

            assertAnswered(Status.Code.INVALID_ARGUMENT, stub.failure())
        }

    @Test
    fun `a body it cannot read is a server that answered`() =
        runTest {
            val stub = connected()
            seerr.enqueue(MockResponse(code = 200, headers = Headers.headersOf("Content-Type", "application/json"), body = "not json"))

            val failure = stub.failure()

            assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
            assertNull(serverUnreachableOf(failure))
        }

    @Test
    fun `a signed-in user declares the capability`() =
        runTest {
            val stub = connected()
            seerr.enqueueProfile(json(PROFILE), json(SETTINGS))
            seerr.enqueue(json(USER))

            val response = stub.handshake(HandshakeRequest.getDefaultInstance())

            assertTrue(Capability.CAPABILITY_SERVER_UNREACHABLE in response.capabilitiesList)
        }

    private fun answer(code: Int): MockResponse =
        MockResponse(code = code, headers = Headers.headersOf("Content-Type", "application/json"), body = "{}")

    private fun assertAnswered(
        code: Status.Code,
        failure: StatusException,
    ) {
        assertEquals(code, failure.status.code)
        assertNull(serverUnreachableOf(failure))
    }

    private suspend fun RequestServiceGrpcKt.RequestServiceCoroutineStub.failure(
        call: suspend RequestServiceGrpcKt.RequestServiceCoroutineStub.() -> Any = {
            getStatus(GetStatusRequest.newBuilder().setMedia(movie).build())
        },
    ): StatusException =
        try {
            call()
            error("the call should have failed")
        } catch (e: StatusException) {
            e
        }

    /** Signed in against the scripted server, with the profile and user read once so a later failure is the network's. */
    private fun connected(
        title: String? = null,
        warm: Boolean = true,
    ): RequestServiceGrpcKt.RequestServiceCoroutineStub {
        val store =
            CredentialStore(
                dataStore =
                    PreferenceDataStoreFactory.create {
                        folder.newFile(
                            "creds-${folder.root.list().orEmpty().size}.preferences_pb",
                        )
                    },
                cipher = com.cooper.binge.companion.seerr.util.PlainCipher,
                io = Dispatchers.Unconfined,
            )
        runBlocking { store.save(SeerrCredentials(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"), SeerrVariant.fromVersion("2.7.0"))) }
        val transport =
            Interceptor { chain ->
                when {
                    timingOut.get() -> throw SocketTimeoutException("timeout")
                    untrusted.get() -> throw SSLHandshakeException("untrusted certificate")
                    else -> chain.proceed(chain.request())
                }
            }
        val connection = SeerrConnection(store, SeerrApiFactory(logRequests = false, testTransport = { transport }))
        val settings = if (title == null) """{"initialized":true}""" else """{"initialized":true,"applicationTitle":"$title"}"""
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
        if (warm) {
            seerr.enqueueProfile(json(PROFILE), json(settings))
            seerr.enqueue(json(USER))
            runBlocking { stub.handshake(HandshakeRequest.getDefaultInstance()) }
            repeat(3) { seerr.takeRequest() }
        }
        return stub
    }

    private fun json(body: String): MockResponse =
        MockResponse(code = 200, headers = Headers.headersOf("Content-Type", "application/json"), body = body)
}
