package io.github.scottcooper92.binge.seerr.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.binge.integration.contracts.request.v1.Availability
import com.binge.integration.contracts.request.v1.GetStatusRequest
import com.binge.integration.contracts.request.v1.HandshakeRequest
import com.binge.integration.contracts.request.v1.RequestServiceGrpcKt
import com.binge.integration.contracts.v1.HostInfo
import com.binge.integration.contracts.v1.MediaId
import com.binge.integration.contracts.v1.MediaType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderChannelBuilder
import io.grpc.binder.SecurityPolicies
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The whole path a host takes to this companion, across a real Binder on the minified build (#86):
 * the exported Service under its caller policy, the generated stubs and the protobuf runtime under
 * R8, then handshake and one status call served from a connected server. The server is a mock in
 * this process; the unit tests beside the service cover its translation, this covers the crossing.
 * The debug policy admits any caller, which is what lets the test bind as one; a release build's
 * pinned policy would refuse it, so the lane runs the minified debug build.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CompanionServiceRoundTripTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var connection: SeerrConnection

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val seerr = MockWebServer()

    @Before
    fun setUp() {
        hilt.inject()
        seerr.dispatcher = FakeSeerr
        seerr.start()
    }

    @After
    fun tearDown() {
        runBlocking { connection.disconnect() }
        seerr.close()
    }

    @Test
    fun handshakeAndStatusCrossTheBinderOnTheMinifiedBuild() =
        runBlocking {
            val connected = connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"))
            assertTrue("the mock server was not accepted as a connection: $connected", connected.isSuccess)

            val channel =
                BinderChannelBuilder
                    .forAddress(AndroidComponentAddress.forRemoteComponent(app.packageName, SERVICE_CLASS), app)
                    .securityPolicy(SecurityPolicies.internalOnly())
                    .build()
            try {
                val stub = RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)

                val handshake = withTimeout(CALL_TIMEOUT_MS) { stub.handshake(handshakeRequest()) }
                assertTrue("no capabilities declared: $handshake", handshake.capabilitiesCount > 0)
                assertTrue("no provider name declared", handshake.providerName.isNotBlank())

                val status = withTimeout(CALL_TIMEOUT_MS) { stub.getStatus(GetStatusRequest.newBuilder().setMedia(MOVIE).build()) }
                assertEquals(Availability.AVAILABILITY_AVAILABLE, status.status.availability)
            } finally {
                channel.shutdownNow()
            }
        }

    private fun handshakeRequest(): HandshakeRequest =
        HandshakeRequest
            .newBuilder()
            .setHost(
                HostInfo
                    .newBuilder()
                    .setPackageName(app.packageName)
                    .setVersionName("androidTest")
                    .setVersionCode(1),
            ).build()

    /** The four answers a handshake and a status call read: the profile, the signed-in user and one title. */
    private object FakeSeerr : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            when (request.url.encodedPath) {
                "/api/v1/status" -> json("""{"version":"2.7.0"}""")
                "/api/v1/settings/public" -> json("""{"initialized":true}""")
                "/api/v1/auth/me" -> json("""{"id":1,"permissions":$ADMIN}""")
                "/api/v1/movie/${MOVIE.tmdbId}" -> json("""{"mediaInfo":{"id":9,"status":$MEDIA_AVAILABLE,"requests":[]}}""")
                else -> MockResponse(code = HTTP_NOT_FOUND)
            }

        private fun json(body: String): MockResponse =
            MockResponse(code = HTTP_OK, headers = Headers.headersOf("Content-Type", "application/json"), body = body)
    }

    private companion object {
        const val SERVICE_CLASS = "io.github.scottcooper92.binge.seerr.service.SeerrCompanionService"
        const val CALL_TIMEOUT_MS = 10_000L
        const val ADMIN = 2
        const val MEDIA_AVAILABLE = 5
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        val MOVIE: MediaId =
            MediaId
                .newBuilder()
                .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
                .setTmdbId(603)
                .build()
    }
}
