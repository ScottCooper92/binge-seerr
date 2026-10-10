package io.github.scottcooper92.binge.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.request.v1.ApproveRequestRequest
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.BlockTitleRequest
import com.binge.companion.contracts.request.v1.CancelRequestRequest
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.DestinationChoices
import com.binge.companion.contracts.request.v1.EditRequestRequest
import com.binge.companion.contracts.request.v1.GetAdvancedRequestOptionsRequest
import com.binge.companion.contracts.request.v1.GetAttentionRequest
import com.binge.companion.contracts.request.v1.GetDestinationOptionsRequest
import com.binge.companion.contracts.request.v1.GetStatusRequest
import com.binge.companion.contracts.request.v1.GetStatusesRequest
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.IssueType
import com.binge.companion.contracts.request.v1.ListRequestsRequest
import com.binge.companion.contracts.request.v1.ListRequestsResponse
import com.binge.companion.contracts.request.v1.ObserveAttentionRequest
import com.binge.companion.contracts.request.v1.ObserveStatusRequest
import com.binge.companion.contracts.request.v1.ReportIssueRequest
import com.binge.companion.contracts.request.v1.RequestFilter
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.RetryRequestRequest
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.UnblockTitleRequest
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.MAX_SEASON_NUMBERS
import io.github.scottcooper92.binge.seerr.auth.BingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.NoBingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.CachedStatus
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
import io.github.scottcooper92.binge.seerr.data.NoMediaStatusStore
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.util.RecordingAnalytics
import io.github.scottcooper92.binge.seerr.util.enqueueProfile
import io.github.scottcooper92.binge.seerr.util.routeProfiles
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ADMIN = 2
private const val REQUEST = 1 shl 5
private const val REQUEST_4K = 1 shl 10
private const val REQUEST_4K_MOVIE = 1 shl 11
private const val REQUEST_ADVANCED = 1 shl 13
private const val REQUEST_4K_PERMISSION = 1 shl 10
private const val CREATE_ISSUES = 1 shl 22

private const val ALL_4K_ENABLED = """{"initialized":true,"movie4kEnabled":true,"series4kEnabled":true}"""

/** Seerr's record for a movie whose only version is the 4K one: `status` Unknown, `status4k` Available. */
private const val AVAILABLE_ONLY_IN_4K = """{"mediaInfo":{"id":9,"status":1,"status4k":5}}"""

/**
 * A movie downloading in both versions: 1,000 bytes on the standard server, half done, and 3,000 on
 * the 4K one, not started. Seerr reports them in `downloadStatus` and `downloadStatus4k`.
 */
private const val DOWNLOADING_IN_BOTH =
    """{"mediaInfo":{"id":9,"status":3,"status4k":3,""" +
        """"downloadStatus":[{"title":"Standard","size":1000,"sizeLeft":500,"status":"downloading"}],""" +
        """"downloadStatus4k":[{"title":"UHD","size":3000,"sizeLeft":3000,"status":"queued"}]}}"""

/** A movie downloading only in 4K. */
private const val DOWNLOADING_ONLY_IN_4K =
    """{"mediaInfo":{"id":9,"status":1,"status4k":3,"downloadStatus4k":[{"title":"UHD","size":3000,"sizeLeft":1500}]}}"""

private const val MOVIE_4K_ENABLED = """{"initialized":true,"movie4kEnabled":true}"""

/** Seasons 1 to 3 as the server's show details list them, which an edit's seasons are checked against (#1002). */
private const val SHOW_WITH_THREE_SEASONS =
    """{"seasons":[{"seasonNumber":1,"episodeCount":10},{"seasonNumber":2,"episodeCount":10},{"seasonNumber":3,"episodeCount":10}]}"""

/**
 * The whole contract end to end, on the JVM: a host's generated stub over an in-process channel
 * into this service, and this service over real HTTP into a scripted Seerr. What it pins is the
 * translation — permissions to capabilities, Seerr's ids and codes to the contract's — and that
 * the gate on a capability is enforced here, not trusted to the host.
 */
class SeerrRequestServiceTest {
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

    private fun connected(
        permissions: Int = ADMIN,
        version: String = "2.7.0",
        cache: MediaStatusStore = NoMediaStatusStore,
        now: () -> Long = { 0L },
        bingeConnection: BingeConnectionStore = NoBingeConnectionStore,
        analytics: Analytics = NoOpAnalytics,
        warm: Boolean = true,
        publicSettings: String = """{"initialized":true}""",
    ): RequestServiceGrpcKt.RequestServiceCoroutineStub {
        val store =
            CredentialStore(
                dataStore = PreferenceDataStoreFactory.create { folder.newFile("creds.preferences_pb") },
                cipher = PlainCipher,
            )
        runBlocking { store.save(SeerrCredentials(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"), SeerrVariant.fromVersion(version))) }
        val connection = SeerrConnection(store, SeerrApiFactory(logRequests = false))
        if (warm) {
            seerr.enqueueProfile(json("""{"version":"$version"}"""), json(publicSettings))
            seerr.enqueue(json("""{"id":1,"permissions":$permissions}"""))
        }
        val stub =
            serve(
                SeerrRequestService(
                    connection,
                    versionName = "0.1.0-test",
                    clock = now,
                    observeIntervalMillis = 1,
                    attentionIntervalMillis = 1,
                    statusCache = cache,
                    bingeConnection = bingeConnection,
                    analytics = analytics,
                ),
            )
        // One handshake up front consumes the profile's two answers and `auth/me` and caches all
        // three, so each test's recorded requests are its own rather than starting with lookups.
        // A cold start skips it: the process has cached nothing, as after the app is killed.
        if (warm) {
            runBlocking { stub.handshake(HandshakeRequest.getDefaultInstance()) }
            repeat(3) { seerr.takeRequest() }
        }
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

    /** A handshake re-reads the profile and the user, so each one after the warm-up is answered from the server again. */
    private suspend fun RequestServiceGrpcKt.RequestServiceCoroutineStub.handshakeAs(
        permissions: Int,
        version: String = "2.7.0",
        publicSettings: String = """{"initialized":true}""",
    ): HandshakeResponse {
        seerr.enqueueProfile(json("""{"version":"$version"}"""), json(publicSettings))
        seerr.enqueue(json("""{"id":1,"permissions":$permissions}"""))
        return handshake(HandshakeRequest.getDefaultInstance()).also { repeat(3) { seerr.takeRequest() } }
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

            val response = stub.handshakeAs(REQUEST or CREATE_ISSUES)

            assertEquals("Jellyseerr", response.providerName)
            assertEquals(
                setOf(
                    Capability.CAPABILITY_OBSERVE_STATUS,
                    Capability.CAPABILITY_ATTENTION,
                    Capability.CAPABILITY_CANCEL,
                    Capability.CAPABILITY_EDIT_SEASONS,
                    Capability.CAPABILITY_REPORT_ISSUE,
                    Capability.CAPABILITY_LIST_REQUESTS,
                    Capability.CAPABILITY_BATCH_STATUS,
                ),
                response.capabilitiesList.toSet(),
            )
        }

    /**
     * The capability set is decided at the handshake and the user behind it is cached for the life of the
     * connection, so without a refresh a host that rebinds while this process is alive is told what the
     * user could do when the process started.
     */
    @Test
    fun `a rebind is told what the user may do now, not when the process started`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            assertFalse(Capability.CAPABILITY_REQUEST_4K in stub.handshakeAs(REQUEST).capabilitiesList)

            val response = stub.handshakeAs(REQUEST or REQUEST_4K_PERMISSION, publicSettings = MOVIE_4K_ENABLED)

            assertTrue(Capability.CAPABILITY_REQUEST_4K in response.capabilitiesList)
        }

    @Test
    fun `a successful handshake records that Binge has connected`() =
        runTest {
            val recording = RecordingBingeConnectionStore()

            // connected() itself performs one handshake as setup, which is what this asserts landed.
            connected(bingeConnection = recording)

            assertTrue(recording.recorded)
        }

    @Test
    fun `an admin declares everything`() =
        runTest {
            val response = connected(permissions = ADMIN).handshakeAs(ADMIN, publicSettings = ALL_4K_ENABLED)

            // In the contract but deliberately not offered: MEDIA_FILE_INFO would need a Radarr key and host (#483).
            assertEquals(
                Capability.entries.toSet() - Capability.UNRECOGNIZED - Capability.CAPABILITY_UNSPECIFIED -
                    Capability.CAPABILITY_MEDIA_FILE_INFO,
                response.capabilitiesList.toSet(),
            )
        }

    @Test
    fun `overseerr has no blocklist, so an admin there is not offered the block capability`() =
        runTest {
            val response = connected(permissions = ADMIN, version = "1.33.2").handshakeAs(ADMIN, version = "1.33.2")

            assertEquals("Overseerr", response.providerName)
            assertFalse(Capability.CAPABILITY_BLOCK in response.capabilitiesList)
            assertTrue(Capability.CAPABILITY_REPORT_ISSUE in response.capabilitiesList)
        }

    @Test
    fun `a block posts to blacklist on jellyseerr 2`() =
        runTest {
            val stub = connected(version = "2.7.0")
            seerr.enqueue(MockResponse(code = 201))

            stub.blockTitle(
                BlockTitleRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setTitle("The Matrix")
                    .build(),
            )

            val posted = seerr.takeRequest()
            assertEquals("/api/v1/blacklist", posted.url.encodedPath)
            assertTrue(
                posted.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"user\":1"),
            )
        }

    @Test
    fun `a block posts to blocklist from seerr 3`() =
        runTest {
            val stub = connected(version = "3.1.0")
            seerr.enqueue(MockResponse(code = 201))

            stub.blockTitle(
                BlockTitleRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setTitle("The Matrix")
                    .build(),
            )

            val posted = seerr.takeRequest()
            assertEquals("/api/v1/blocklist", posted.url.encodedPath)
            assertTrue(
                posted.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"user\":1"),
            )
        }

    /** The contract says blocking a title already blocklisted is OK and changes nothing (#1000). */
    @Test
    fun `blocking a title already on the blocklist is OK, whichever database seerr keeps it in`() =
        runTest {
            val stub = connected(version = "3.1.0")
            // SQLite: Seerr's own words for the unique-key clash. Postgres: the same clash as a generic 409.
            seerr.enqueue(MockResponse(code = 412, body = """{"message":"Item already blocklisted"}"""))
            seerr.enqueue(MockResponse(code = 409, body = """{"message":"Something wrong"}"""))

            repeat(2) { stub.blockTitle(matrixBlock) }

            assertEquals("/api/v1/blocklist", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `blocking a title already on jellyseerr 2's blacklist is OK`() =
        runTest {
            val stub = connected(version = "2.7.0")
            seerr.enqueue(MockResponse(code = 412, body = """{"message":"Item already blacklisted"}"""))

            stub.blockTitle(matrixBlock)

            assertEquals("/api/v1/blacklist", seerr.takeRequest().url.encodedPath)
        }

    private val matrixBlock: BlockTitleRequest =
        BlockTitleRequest
            .newBuilder()
            .setMedia(movie)
            .setTitle("The Matrix")
            .build()

    @Test
    fun `any other refusal of a block still reaches the host`() =
        runTest {
            val stub = connected(version = "3.1.0")
            seerr.enqueue(MockResponse(code = 500, body = """{"message":"boom"}"""))

            assertEquals(
                Status.Code.UNAVAILABLE,
                stub.code { blockTitle(matrixBlock) },
            )
        }

    @Test
    fun `an unblock deletes the title's blocklist entry by tmdb id and media type`() =
        runTest {
            val stub = connected(version = "3.2.0")
            seerr.enqueue(MockResponse(code = 204))

            stub.unblockTitle(UnblockTitleRequest.newBuilder().setMedia(movie).build())

            val deleted = seerr.takeRequest()
            assertEquals("DELETE", deleted.method)
            assertEquals("/api/v1/blocklist/603", deleted.url.encodedPath)
            assertEquals("movie", deleted.url.queryParameter("mediaType"))
        }

    @Test
    fun `an unblock deletes from blacklist on jellyseerr 2`() =
        runTest {
            val stub = connected(version = "2.7.0")
            seerr.enqueue(MockResponse(code = 204))

            stub.unblockTitle(UnblockTitleRequest.newBuilder().setMedia(movie).build())

            val deleted = seerr.takeRequest()
            assertEquals("/api/v1/blacklist/603", deleted.url.encodedPath)
            assertNull(deleted.url.queryParameter("mediaType"))
        }

    /** The contract's NOT_FOUND for a title not on the blocklist is the server's own 404. */
    @Test
    fun `unblocking a title that is not blocked is not found`() =
        runTest {
            val stub = connected(version = "3.1.0")
            seerr.enqueue(MockResponse(code = 404))

            assertEquals(
                Status.Code.NOT_FOUND,
                stub.code { unblockTitle(UnblockTitleRequest.newBuilder().setMedia(movie).build()) },
            )
        }

    /** Jellyseerr 2.x's blacklist answers 401 for a title it does not hold, with the session fine (#998). */
    @Test
    fun `unblocking a title jellyseerr 2 does not hold is not found, not a dead session`() =
        runTest {
            val stub = connected(version = "2.7.0")
            seerr.enqueue(MockResponse(code = 401, body = """{"message":"Could not find any entity of type Blacklist"}"""))
            seerr.enqueue(json("""{"id":1,"permissions":$ADMIN}"""))

            assertEquals(
                Status.Code.NOT_FOUND,
                stub.code { unblockTitle(UnblockTitleRequest.newBuilder().setMedia(movie).build()) },
            )
            assertEquals("/api/v1/blacklist/603", seerr.takeRequest().url.encodedPath)
            assertEquals("/api/v1/auth/me", seerr.takeRequest().url.encodedPath)
        }

    /** The Jellyseerr 2.x bug (#539) that only a device log showed: a 400 on one operation, one lineage and version. */
    @Test
    fun `a failed operation is reported with its status and server version and nothing that identifies the title or host`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(version = "2.7.0", analytics = analytics)
            seerr.enqueue(MockResponse(code = 400, body = """{"message":"Unknown query parameter 'mediaType'"}"""))

            assertEquals(Status.Code.INVALID_ARGUMENT, stub.code { unblockTitle(UnblockTitleRequest.newBuilder().setMedia(movie).build()) })

            val expected =
                mapOf(
                    "operation" to "unblock_title",
                    "http_status" to "400",
                    "grpc_status" to "INVALID_ARGUMENT",
                    "server_lineage" to "jellyseerr",
                    "server_version" to "2.7.0",
                )
            assertEquals(listOf("request_operation_failed" to expected), analytics.events)
            val everything =
                analytics.events
                    .single()
                    .second.values
                    .joinToString()
            assertTrue(!everything.contains("603") && !everything.contains(seerr.url("/").host) && !everything.contains("mediaType"))
        }

    @Test
    fun `an expired sign-in is not reported`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(version = "3.2.0", analytics = analytics)
            seerr.enqueue(MockResponse(code = 401))

            assertEquals(Status.Code.UNAUTHENTICATED, stub.code { unblockTitle(UnblockTitleRequest.newBuilder().setMedia(movie).build()) })

            assertEquals(emptyList<Any>(), analytics.events)
        }

    private fun failedOperation(analytics: RecordingAnalytics): Map<String, String> {
        val (name, params) = analytics.events.single()
        assertEquals("request_operation_failed", name)
        return params.mapValues { it.value.toString() }
    }

    @Test
    fun `a failed submit is reported, and a created, conflicting or refused one is not`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(version = "2.7.0", analytics = analytics)
            val request = SubmitRequestRequest.newBuilder().setMedia(movie).build()

            seerr.enqueue(json("""{"id":77}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            stub.submitRequest(request)
            seerr.enqueue(MockResponse(code = 409))
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            stub.submitRequest(request)
            assertEquals(Status.Code.INVALID_ARGUMENT, stub.code { submitRequest(request.toBuilder().setIs4K(true).build()) })
            assertEquals(emptyList<Any>(), analytics.events)

            seerr.enqueue(MockResponse(code = 500, body = """{"message":"boom 603"}"""))
            stub.code { submitRequest(request) }

            val reported = failedOperation(analytics)
            assertEquals("submit_request", reported["operation"])
            assertEquals("500", reported["http_status"])
            assertEquals("jellyseerr", reported["server_lineage"])
            assertEquals("2.7.0", reported["server_version"])
            assertTrue(
                analytics.events
                    .single()
                    .second.values
                    .none { "603" in it.toString() || "boom" in it.toString() },
            )
        }

    @Test
    fun `a failed status read is reported, and a successful one is not`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(analytics = analytics)
            val request = GetStatusRequest.newBuilder().setMedia(movie).build()

            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            stub.getStatus(request)
            assertEquals(emptyList<Any>(), analytics.events)

            seerr.enqueue(MockResponse(code = 500))
            stub.code { getStatus(GetStatusRequest.newBuilder().setMedia(movie.toBuilder().setTmdbId(604)).build()) }

            assertEquals("get_status", failedOperation(analytics)["operation"])
        }

    @Test
    fun `a failed attention read is reported, and an expired sign-in is not`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(permissions = ADMIN, analytics = analytics)

            // A dead session: the 401, then auth/me refusing the interceptor's probe too (#997).
            seerr.enqueue(MockResponse(code = 401))
            seerr.enqueue(MockResponse(code = 401))
            assertTrue(stub.getAttention(GetAttentionRequest.getDefaultInstance()).attention.needsReconnect)
            assertEquals(emptyList<Any>(), analytics.events)

            seerr.enqueue(MockResponse(code = 500))
            stub.code { getAttention(GetAttentionRequest.getDefaultInstance()) }

            assertEquals("get_attention", failedOperation(analytics)["operation"])
        }

    @Test
    fun `a failed handshake is reported`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(analytics = analytics)

            seerr.enqueueProfile(json("""{"version":"2.7.0"}"""), json("""{"initialized":true}"""))
            seerr.enqueue(MockResponse(code = 500))
            stub.code { handshake(HandshakeRequest.getDefaultInstance()) }

            val reported = failedOperation(analytics)
            assertEquals("handshake", reported["operation"])
            assertEquals("500", reported["http_status"])
        }

    @Test
    fun `a failing status stream is reported once, and a healthy one never`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(analytics = analytics)
            val request = ObserveStatusRequest.newBuilder().setMedia(movie).build()

            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            stub.observeStatus(request).take(1).toList()
            assertEquals(emptyList<Any>(), analytics.events)

            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))
            seerr.enqueue(MockResponse(code = 500))
            assertEquals(Status.Code.UNAVAILABLE, stub.code { stub.observeStatus(request).toList() })

            assertEquals("observe_status", failedOperation(analytics)["operation"])
        }

    @Test
    fun `a failing attention stream is reported once`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(permissions = ADMIN, analytics = analytics)
            seerr.enqueue(json("""{"pending":1}"""))
            seerr.enqueue(json("""{"open":0}"""))
            seerr.enqueue(MockResponse(code = 500))

            stub.code { stub.observeAttention(ObserveAttentionRequest.getDefaultInstance()).toList() }

            assertEquals("observe_attention", failedOperation(analytics)["operation"])
        }

    @Test
    fun `reporting a quota refusal leaves it a quota refusal`() =
        runTest {
            val analytics = RecordingAnalytics()
            val stub = connected(analytics = analytics)
            seerr.enqueue(MockResponse(code = 403, body = """{"message":"Quota exceeded"}"""))

            assertEquals(
                Status.Code.RESOURCE_EXHAUSTED,
                stub.code { unblockTitle(UnblockTitleRequest.newBuilder().setMedia(movie).build()) },
            )

            assertEquals("RESOURCE_EXHAUSTED", analytics.events.single().second["grpc_status"])
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
            val stub = connected(publicSettings = MOVIE_4K_ENABLED)
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
    fun `advanced options lists every server for the shape, preselected on the plain non-4k default`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED or REQUEST_4K, publicSettings = MOVIE_4K_ENABLED)

            seerr.enqueue(
                json(
                    """
                    [
                      {"id":1,"name":"Main","is4k":false,"isDefault":true,"activeProfileId":4,"activeDirectory":"/media"},
                      {"id":2,"name":"Main 4K","is4k":true,"isDefault":false,"activeProfileId":9,"activeDirectory":"/media4k"}
                    ]
                    """.trimIndent(),
                ),
            )
            seerr.enqueue(
                json(
                    """{"profiles":[{"id":4,"name":"HD"},{"id":5,"name":"UHD"}],"rootFolders":[{"id":1,"path":"/media"},{"id":2,"path":"/kids"}]}""",
                ),
            )

            val destination =
                stub.getAdvancedRequestOptions(GetAdvancedRequestOptionsRequest.newBuilder().setMedia(movie).build()).destination

            assertEquals(listOf(1 to false, 2 to true), destination.serversList.map { it.id.toInt() to it.is4K })
            assertEquals("1", destination.selectedServerId)
            assertEquals(listOf("4" to "HD", "5" to "UHD"), destination.profilesList.map { it.id to it.label })
            assertEquals("4", destination.selectedProfileId)
            assertEquals(listOf("/media", "/kids"), destination.rootFoldersList.map { it.id })
            assertEquals("/media", destination.selectedRootFolderId)
            assertEquals("/api/v1/service/radarr", seerr.takeRequest().url.encodedPath)
            assertEquals("/api/v1/service/radarr/1", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `advanced options for a shape with no configured server comes back empty`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
            seerr.enqueue(json("[]"))

            val destination =
                stub.getAdvancedRequestOptions(GetAdvancedRequestOptionsRequest.newBuilder().setMedia(movie).build()).destination

            assertEquals(DestinationChoices.getDefaultInstance(), destination)
        }

    @Test
    fun `advanced options for a shape with only 4K servers still lists them to a user who may request 4K, nothing preselected`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED or REQUEST_4K, publicSettings = MOVIE_4K_ENABLED)
            val before = seerr.requestCount
            seerr.enqueue(json("""[{"id":5,"name":"4K Only","is4k":true,"isDefault":true}]"""))

            val destination =
                stub.getAdvancedRequestOptions(GetAdvancedRequestOptionsRequest.newBuilder().setMedia(movie).build()).destination

            assertEquals(listOf(5 to true), destination.serversList.map { it.id.toInt() to it.is4K })
            assertEquals("", destination.selectedServerId)
            assertTrue(destination.profilesList.isEmpty())
            assertTrue(destination.rootFoldersList.isEmpty())
            // No default to resolve against, so no arr-server details fetch beyond the server list itself.
            assertEquals(before + 1, seerr.requestCount)
        }

    /** A 4K server is a 4K request, so a user who may not make one is not offered one. */
    @Test
    fun `advanced options leaves out the 4K servers for a user without the 4K permission`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
            seerr.enqueue(
                json(
                    """[{"id":1,"name":"Main","is4k":false,"isDefault":true},{"id":2,"name":"Main 4K","is4k":true,"isDefault":true}]""",
                ),
            )
            seerr.enqueue(json("""{"profiles":[],"rootFolders":[]}"""))

            val destination =
                stub.getAdvancedRequestOptions(GetAdvancedRequestOptionsRequest.newBuilder().setMedia(movie).build()).destination

            assertEquals(listOf(1 to false), destination.serversList.map { it.id.toInt() to it.is4K })
            assertEquals("1", destination.selectedServerId)
        }

    @Test
    fun `destination options for a 4K server, from a user without the 4K permission, is INVALID_ARGUMENT`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
            seerr.enqueue(json("""[{"id":2,"name":"Main 4K","is4k":true}]"""))

            val code =
                stub.code {
                    getDestinationOptions(
                        GetDestinationOptionsRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setServerId("2")
                            .build(),
                    )
                }

            assertEquals(Status.Code.INVALID_ARGUMENT, code)
        }

    @Test
    fun `destination options re-resolves profile and root folder for the server the host moved to`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED or REQUEST_4K, publicSettings = MOVIE_4K_ENABLED)
            seerr.enqueue(
                json(
                    """[{"id":1,"name":"Main","is4k":false,"isDefault":true},{"id":2,"name":"Main 4K","is4k":true,"activeProfileId":9,"activeDirectory":"/media4k"}]""",
                ),
            )
            seerr.enqueue(json("""{"profiles":[{"id":9,"name":"UHD Only"}],"rootFolders":[{"id":3,"path":"/media4k"}]}"""))

            val request =
                GetDestinationOptionsRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setServerId("2")
                    .build()
            val destination = stub.getDestinationOptions(request).destination

            assertTrue(destination.serversList.isEmpty())
            assertEquals("", destination.selectedServerId)
            assertEquals(listOf("9"), destination.profilesList.map { it.id })
            assertEquals("9", destination.selectedProfileId)
            assertEquals("/media4k", destination.selectedRootFolderId)
            seerr.takeRequest()
            assertEquals("/api/v1/service/radarr/2", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `destination options for a server id the server no longer lists is INVALID_ARGUMENT`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
            seerr.enqueue(json("""[{"id":1,"name":"Main","is4k":false,"isDefault":true}]"""))

            assertEquals(
                Status.Code.INVALID_ARGUMENT,
                stub.code {
                    val request =
                        GetDestinationOptionsRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setServerId("99")
                            .build()
                    getDestinationOptions(request)
                },
            )
        }

    @Test
    fun `submit advanced request posts the picker's explicit choices, deriving 4k from the server alone`() =
        runTest {
            // Picking a 4K server is a 4K request, so this user holds that permission too.
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED or REQUEST_4K, publicSettings = MOVIE_4K_ENABLED)
            seerr.enqueue(json("""[{"id":1,"name":"Main","is4k":false},{"id":2,"name":"Main 4K","is4k":true}]"""))
            seerr.enqueue(json("""{"id":88}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))

            val response =
                stub.submitAdvancedRequest(
                    SubmitAdvancedRequestRequest
                        .newBuilder()
                        .setMedia(movie)
                        .setServerId("2")
                        .setProfileId("9")
                        .setRootFolderId("/media4k")
                        .build(),
                )

            assertEquals(88, response.result.requestId)
            // No arr-server details fetch: both axes were explicit, so there was no default to resolve.
            seerr.takeRequest()
            val postedRequest = seerr.takeRequest()
            val posted = postedRequest.body?.utf8().orEmpty()
            assertTrue(posted.contains("\"is4k\":true"))
            assertTrue(posted.contains("\"serverId\":2"))
            assertTrue(posted.contains("\"profileId\":9"))
            assertTrue(posted.contains("\"rootFolder\":\"/media4k\""))
        }

    @Test
    fun `submit advanced request with an untouched picker falls back to the server's own defaults`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
            seerr.enqueue(json("""[{"id":1,"name":"Main","is4k":false,"isDefault":true,"activeProfileId":4,"activeDirectory":"/media"}]"""))
            seerr.enqueue(json("""{"profiles":[{"id":4,"name":"HD"}],"rootFolders":[{"id":1,"path":"/media"}]}"""))
            seerr.enqueue(json("""{"id":89}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))

            stub.submitAdvancedRequest(SubmitAdvancedRequestRequest.newBuilder().setMedia(movie).build())

            repeat(2) { seerr.takeRequest() }
            val postedRequest = seerr.takeRequest()
            val posted = postedRequest.body?.utf8().orEmpty()
            assertTrue(posted.contains("\"is4k\":false"))
            assertTrue(posted.contains("\"serverId\":1"))
            assertTrue(posted.contains("\"profileId\":4"))
            assertTrue(posted.contains("\"rootFolder\":\"/media\""))
        }

    /** A profile that is present but not a number names nothing, so it fails loudly instead of posting to the server's default. */
    @Test
    fun `submit advanced request with a profile id that is not a number is INVALID_ARGUMENT, before anything is posted`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
            seerr.enqueue(json("""[{"id":1,"name":"Main","is4k":false}]"""))
            val before = seerr.requestCount

            val code =
                stub.code {
                    submitAdvancedRequest(
                        SubmitAdvancedRequestRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setServerId("1")
                            .setProfileId("abc")
                            .setRootFolderId("/media")
                            .build(),
                    )
                }

            assertEquals(Status.Code.INVALID_ARGUMENT, code)
            // The server list was read to find the server; nothing was posted after it.
            assertEquals(before + 1, seerr.requestCount)
        }

    private suspend fun declares4k(
        permissions: Int,
        settings: String,
    ): Boolean =
        connected(permissions)
            .handshakeAs(permissions, publicSettings = settings)
            .capabilitiesList
            .contains(Capability.CAPABILITY_REQUEST_4K)

    @Test
    fun `4K is declared where the permission and the server's 4K setting for a media type agree`() =
        runTest {
            assertTrue(declares4k(REQUEST or REQUEST_4K, MOVIE_4K_ENABLED))
        }

    @Test
    fun `4K is not declared while the server has it off, though the user holds the permission`() =
        runTest {
            assertFalse(declares4k(REQUEST or REQUEST_4K, """{"initialized":true}"""))
        }

    @Test
    fun `4K is not declared for a user without the permission, though the server has it on`() =
        runTest {
            assertFalse(declares4k(REQUEST, ALL_4K_ENABLED))
        }

    @Test
    fun `the movie 4K permission does not open a server that only has series 4K on`() =
        runTest {
            assertFalse(declares4k(REQUEST or REQUEST_4K_MOVIE, """{"initialized":true,"series4kEnabled":true}"""))
        }

    @Test
    fun `a 4K request is refused while the server has 4K off, though the user holds the permission`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_4K)
            val before = seerr.requestCount

            val code =
                stub.code {
                    submitRequest(
                        SubmitRequestRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setIs4K(true)
                            .build(),
                    )
                }

            assertEquals(Status.Code.INVALID_ARGUMENT, code)
            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `season edits are not declared while the server has partial requests off`() =
        runTest {
            val response =
                connected(permissions = REQUEST)
                    .handshakeAs(REQUEST, publicSettings = """{"initialized":true,"partialRequestsEnabled":false}""")

            assertTrue(Capability.CAPABILITY_CANCEL in response.capabilitiesList)
            assertFalse(Capability.CAPABILITY_EDIT_SEASONS in response.capabilitiesList)
        }

    @Test
    fun `a 4K request from a user who does not hold the 4K permission is INVALID_ARGUMENT, before it is posted`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            val before = seerr.requestCount

            val code =
                stub.code {
                    submitRequest(
                        SubmitRequestRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setIs4K(true)
                            .build(),
                    )
                }

            assertEquals(Status.Code.INVALID_ARGUMENT, code)
            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `a 4K server named through the advanced path by a user without the 4K permission is INVALID_ARGUMENT`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
            seerr.enqueue(json("""[{"id":2,"name":"Main 4K","is4k":true}]"""))
            val before = seerr.requestCount

            val code =
                stub.code {
                    submitAdvancedRequest(
                        SubmitAdvancedRequestRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setServerId("2")
                            .setProfileId("9")
                            .setRootFolderId("/media4k")
                            .build(),
                    )
                }

            assertEquals(Status.Code.INVALID_ARGUMENT, code)
            assertEquals(before + 1, seerr.requestCount)
        }

    @Test
    fun `a user who holds the 4K permission may still request 4K`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_4K, publicSettings = MOVIE_4K_ENABLED)
            seerr.enqueue(json("""{"id":90}"""))
            seerr.enqueue(json("""{"mediaInfo":{"status":2}}"""))

            val created =
                stub.submitRequest(
                    SubmitRequestRequest
                        .newBuilder()
                        .setMedia(movie)
                        .setIs4K(true)
                        .build(),
                )

            assertEquals(90, created.requestId)
        }

    @Test
    fun `the advanced-request rpcs are refused for a user without the permission, before any request`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            val before = seerr.requestCount

            assertEquals(
                Status.Code.PERMISSION_DENIED,
                stub.code { getAdvancedRequestOptions(GetAdvancedRequestOptionsRequest.newBuilder().setMedia(movie).build()) },
            )
            assertEquals(
                Status.Code.PERMISSION_DENIED,
                stub.code {
                    val request =
                        GetDestinationOptionsRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setServerId("1")
                            .build()
                    getDestinationOptions(request)
                },
            )
            assertEquals(
                Status.Code.PERMISSION_DENIED,
                stub.code { submitAdvancedRequest(SubmitAdvancedRequestRequest.newBuilder().setMedia(movie).build()) },
            )

            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `seerr's failures leave as the contract's status codes`() =
        runTest {
            val stub = connected()

            // A dead session: the 401, then auth/me refusing the interceptor's probe too (#997).
            seerr.enqueue(MockResponse(code = 401))
            seerr.enqueue(MockResponse(code = 401))
            assertEquals(Status.Code.UNAUTHENTICATED, stub.status(movie))

            seerr.enqueue(MockResponse(code = 403, body = """{"message":"Movie Quota exceeded"}"""))
            assertEquals(Status.Code.RESOURCE_EXHAUSTED, stub.submit(movie))

            seerr.enqueue(MockResponse(code = 503))
            assertEquals(Status.Code.UNAVAILABLE, stub.status(movie))
        }

    /**
     * The handshake is the one exception (binge-companions#106): the host gates every call on what it
     * declares, so it answers OK and declares the attention read, which is how the host finds out.
     */
    @Test
    fun `nothing connected is UNAUTHENTICATED everywhere but the handshake`() =
        runTest {
            val store = CredentialStore(PreferenceDataStoreFactory.create { folder.newFile("empty.preferences_pb") }, PlainCipher)
            val stub = serve(SeerrRequestService(SeerrConnection(store, SeerrApiFactory(logRequests = false)), "0.1.0-test"))

            val handshake = stub.handshake(HandshakeRequest.getDefaultInstance())
            assertEquals(listOf(Capability.CAPABILITY_ATTENTION), handshake.capabilitiesList)
            assertEquals("Seerr", handshake.providerName)
            assertEquals(Status.Code.UNAUTHENTICATED, stub.code { getAttention(GetAttentionRequest.getDefaultInstance()) })
            assertEquals(Status.Code.UNAUTHENTICATED, stub.status(movie))
            assertEquals(Status.Code.UNAUTHENTICATED, stub.submit(movie))
        }

    /** A rejected session still handshakes, declaring the attention read that reports it as `needs_reconnect`. */
    @Test
    fun `a rejected session handshakes with only the attention read`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            seerr.enqueueProfile(json("""{"version":"2.7.0"}"""), json("""{"initialized":true}"""))
            seerr.enqueue(MockResponse(code = 401))

            val handshake = stub.handshake(HandshakeRequest.getDefaultInstance())

            assertEquals(listOf(Capability.CAPABILITY_ATTENTION), handshake.capabilitiesList)
            assertEquals("Jellyseerr", handshake.providerName)
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
            assertEquals(
                Status.Code.PERMISSION_DENIED,
                stub.code { unblockTitle(UnblockTitleRequest.newBuilder().setMedia(movie).build()) },
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

    /** The contract: nothing to report against unless the title is available or partially available (#682). */
    @Test
    fun `a report against an untracked title is FAILED_PRECONDITION`() =
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

            assertEquals(Status.Code.FAILED_PRECONDITION, code)
        }

    /** Seerr moves only `status4k` for a 4K request; the contract carries it apart (#704). */
    @Test
    fun `a 4k-only title reads as such to a user who may request 4k`() =
        runTest {
            val stub = connected(publicSettings = MOVIE_4K_ENABLED)
            seerr.enqueue(json(AVAILABLE_ONLY_IN_4K))

            val status = stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie).build()).status

            assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, status.availability)
            assertEquals(Availability.AVAILABILITY_AVAILABLE, status.availability4K)
        }

    /** The contract sets the 4K state only under CAPABILITY_REQUEST_4K, so a version they cannot request is not shown. */
    @Test
    fun `a user who may not request 4k is told nothing about the 4k version`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(AVAILABLE_ONLY_IN_4K))

            val status = stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie).build()).status

            assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, status.availability)
            assertEquals(Availability.AVAILABILITY_UNSPECIFIED, status.availability4K)
        }

    /** `download` is every active download, so a user who may request 4K sees the 4K ones in it too (#724). */
    @Test
    fun `the download takes in the 4k downloads for a user who may request 4k`() =
        runTest {
            val stub = connected(publicSettings = MOVIE_4K_ENABLED)
            seerr.enqueue(json(DOWNLOADING_IN_BOTH))

            val download = stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie).build()).status.download

            assertEquals(4_000L, download.totalBytes)
            assertEquals(0.125f, download.fraction, 0.0001f)
            assertEquals("Standard", download.label)
        }

    @Test
    fun `the download leaves the 4k downloads out for a user who may not request 4k`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(DOWNLOADING_IN_BOTH))

            val download = stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie).build()).status.download

            assertEquals(1_000L, download.totalBytes)
            assertEquals(0.5f, download.fraction, 0.0001f)
        }

    @Test
    fun `a title downloading only in 4k shows no download to a user who may not request 4k`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(DOWNLOADING_ONLY_IN_4K))

            assertFalse(stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie).build()).status.hasDownload())
        }

    /** The cache keeps the server's full answer, so a cached read has to leave the 4K downloads out the same way. */
    @Test
    fun `a cached status leaves the 4k downloads out for a user who may not request 4k`() =
        runTest {
            val cache = FakeStatusCache()
            val stub = connected(cache = cache)
            seerr.enqueue(json(DOWNLOADING_IN_BOTH))
            getStatus(stub)
            val before = seerr.requestCount

            val download = getStatus(stub).download

            assertEquals("served from the cache", before, seerr.requestCount)
            assertEquals(1_000L, download.totalBytes)
        }

    @Test
    fun `a report against a title available only in 4k is filed for a user who may request 4k`() =
        runTest {
            val stub = connected(publicSettings = MOVIE_4K_ENABLED)
            seerr.enqueue(json(AVAILABLE_ONLY_IN_4K))
            seerr.enqueue(MockResponse(code = 201))

            stub.reportIssue(
                ReportIssueRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setType(IssueType.ISSUE_TYPE_VIDEO)
                    .build(),
            )

            seerr.takeRequest()
            assertEquals("/api/v1/issue", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `a report against a title available only in 4k is FAILED_PRECONDITION for a user who may not request 4k`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json(AVAILABLE_ONLY_IN_4K))

            val failure =
                runCatching {
                    stub.reportIssue(
                        ReportIssueRequest
                            .newBuilder()
                            .setMedia(movie)
                            .setType(IssueType.ISSUE_TYPE_VIDEO)
                            .build(),
                    )
                }.exceptionOrNull() as StatusException

            assertEquals(Status.Code.FAILED_PRECONDITION, failure.status.code)
            val description = failure.status.description.orEmpty()
            assertFalse(description, description.contains("4K", ignoreCase = true))
        }

    @Test
    fun `a report against a title still pending is FAILED_PRECONDITION, and creates no issue`() =
        runTest {
            val stub = connected()
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":2}}"""))
            val before = seerr.requestCount

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

            assertEquals(Status.Code.FAILED_PRECONDITION, code)
            assertEquals(before + 1, seerr.requestCount)
        }

    /** An unusable MediaId is INVALID_ARGUMENT, not a 404 the host would read as "refresh your view" (#682). */
    @Test
    fun `a tmdb id of zero is INVALID_ARGUMENT, before any request`() =
        runTest {
            val stub = connected()
            val before = seerr.requestCount
            val zero = movie.toBuilder().setTmdbId(0).build()

            assertEquals(Status.Code.INVALID_ARGUMENT, stub.status(zero))
            assertEquals(Status.Code.INVALID_ARGUMENT, stub.submit(zero))
            assertEquals(before, seerr.requestCount)
        }

    /** Seerr refuses a blocklisted title with 403 "This media is blocklisted."; the contract calls it FAILED_PRECONDITION (#682). */
    @Test
    fun `a submit seerr refuses as blocklisted is FAILED_PRECONDITION`() =
        runTest {
            val stub = connected()
            seerr.enqueue(MockResponse(code = 403, body = """{"message":"This media is blocklisted."}"""))

            assertEquals(Status.Code.FAILED_PRECONDITION, stub.submit(movie))
        }

    @Test
    fun `a submit for a title the cache knows is blocklisted is refused without asking seerr`() =
        runTest {
            val cache = FakeStatusCache()
            val stub = connected(cache = cache)
            cache.put(
                movie,
                CachedStatus(RequestStatus.newBuilder().setAvailability(Availability.AVAILABILITY_BLOCKLISTED).build(), 0L),
            )
            val before = seerr.requestCount

            assertEquals(Status.Code.FAILED_PRECONDITION, stub.submit(movie))
            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `a submit for a title whose cached blocklisted row is stale goes to seerr`() =
        runTest {
            val cache = FakeStatusCache()
            val stub = connected(cache = cache, now = { 24 * 60 * 60 * 1000L })
            cache.put(
                movie,
                CachedStatus(RequestStatus.newBuilder().setAvailability(Availability.AVAILABILITY_BLOCKLISTED).build(), 0L),
            )
            val before = seerr.requestCount

            stub.submit(movie)

            assertTrue(seerr.requestCount > before)
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

    /** Every lineage's 401 for a request this user may not delete is not the session (#997), so the host is not sent to reconnect. */
    @Test
    fun `cancelling a request this user may not delete is PERMISSION_DENIED, or FAILED_PRECONDITION once it is past pending`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            val cancel = CancelRequestRequest.newBuilder().setRequestId(4).build()
            val refused = """{"message":"You do not have permission to delete this request."}"""

            // Someone else's pending request: the refusal, then auth/me still answering, then the request read.
            seerr.enqueue(MockResponse(code = 401, body = refused))
            seerr.enqueue(json("""{"id":1,"permissions":$REQUEST}"""))
            seerr.enqueue(json("""{"id":4,"status":1,"media":{"tmdbId":603,"mediaType":"movie"}}"""))
            assertEquals(Status.Code.PERMISSION_DENIED, stub.code { cancelRequest(cancel) })

            // Their own, approved between the host's last read and the tap.
            seerr.enqueue(MockResponse(code = 401, body = refused))
            seerr.enqueue(json("""{"id":1,"permissions":$REQUEST}"""))
            seerr.enqueue(json("""{"id":4,"status":2,"media":{"tmdbId":603,"mediaType":"movie"}}"""))
            assertEquals(Status.Code.FAILED_PRECONDITION, stub.code { cancelRequest(cancel) })
        }

    @Test
    fun `a 401 on cancel while auth_me is refused too is still the session`() =
        runTest {
            val stub = connected(version = "1.33.0")
            seerr.enqueue(MockResponse(code = 401))
            seerr.enqueue(MockResponse(code = 401))

            assertEquals(
                Status.Code.UNAUTHENTICATED,
                stub.code { cancelRequest(CancelRequestRequest.newBuilder().setRequestId(4).build()) },
            )
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

    @Test
    fun `attention counts what waits on a moderator, from both endpoints`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            seerr.enqueue(json("""{"total":9,"pending":3}"""))
            seerr.enqueue(json("""{"total":4,"open":2}"""))

            val attention = stub.getAttention(GetAttentionRequest.getDefaultInstance()).attention

            assertEquals(5, attention.pendingCount)
            assertFalse(attention.needsReconnect)
            assertEquals(listOf("/api/v1/request/count", "/api/v1/issue/count"), List(2) { seerr.takeRequest().url.encodedPath })
        }

    @Test
    fun `a plain requester has nothing waiting on them, and the server is not asked`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            val before = seerr.requestCount

            val attention = stub.getAttention(GetAttentionRequest.getDefaultInstance()).attention

            assertEquals(0, attention.pendingCount)
            assertFalse(attention.needsReconnect)
            assertEquals(before, seerr.requestCount)
        }

    /** A rejected session on a connected server is the contract's flag, not a failure: only this app can mend it. */
    @Test
    fun `a rejected session reads as needs_reconnect`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            // A 401 is the session outright: nothing asks `auth/me` again.
            seerr.enqueue(MockResponse(code = 401))

            val attention = stub.getAttention(GetAttentionRequest.getDefaultInstance()).attention

            assertTrue(attention.needsReconnect)
            assertEquals(0, attention.pendingCount)
        }

    /** Every lineage answers a dead session on `auth/me` with 403, not 401 (#672). */
    @Test
    fun `a cold start whose session auth_me refuses with 403 reads as needs_reconnect`() =
        runTest {
            val stub = connected(permissions = ADMIN, warm = false)
            seerr.enqueue(MockResponse(code = 403))

            val attention = stub.getAttention(GetAttentionRequest.getDefaultInstance()).attention

            assertTrue(attention.needsReconnect)
        }

    /** A count refused with 403 is a session gone stale behind the cache only if `auth/me` now refuses too. */
    @Test
    fun `a count refused while auth_me still answers is the error it is, not needs_reconnect`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            seerr.enqueue(MockResponse(code = 403))
            seerr.enqueue(json("""{"id":1,"permissions":$ADMIN}"""))

            assertEquals(Status.Code.PERMISSION_DENIED, stub.code { getAttention(GetAttentionRequest.getDefaultInstance()) })
        }

    @Test
    fun `a count refused because the session expired behind the cache reads as needs_reconnect`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            seerr.enqueue(MockResponse(code = 403))
            seerr.enqueue(MockResponse(code = 403))

            assertTrue(stub.getAttention(GetAttentionRequest.getDefaultInstance()).attention.needsReconnect)
        }

    @Test
    fun `a session auth_me refuses with 403 handshakes with only the attention read`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            seerr.enqueueProfile(json("""{"version":"2.7.0"}"""), json("""{"initialized":true}"""))
            seerr.enqueue(MockResponse(code = 403))

            assertEquals(listOf(Capability.CAPABILITY_ATTENTION), stub.handshake(HandshakeRequest.getDefaultInstance()).capabilitiesList)
        }

    /**
     * The KDoc's promise, without the process being warm: with nothing cached the first read is
     * `auth/me`, and it is that call the expired session fails on.
     */
    @Test
    fun `a cold start with an expired session reads as needs_reconnect, not UNAUTHENTICATED`() =
        runTest {
            val stub = connected(permissions = ADMIN, warm = false)
            seerr.enqueue(MockResponse(code = 401))

            val attention = stub.getAttention(GetAttentionRequest.getDefaultInstance()).attention

            assertTrue(attention.needsReconnect)
            assertEquals(0, attention.pendingCount)
        }

    @Test
    fun `observe pushes attention only when it changes`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            repeat(2) {
                seerr.enqueue(json("""{"pending":1}"""))
                seerr.enqueue(json("""{"open":0}"""))
            }
            seerr.enqueue(json("""{"pending":1}"""))
            seerr.enqueue(json("""{"open":4}"""))

            val seen = stub.observeAttention(ObserveAttentionRequest.getDefaultInstance()).take(2).toList()

            assertEquals(listOf(1, 5), seen.map { it.attention.pendingCount })
        }

    @Test
    fun `an edit sends the request's own media type and destination with the new season set`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            seerr.enqueue(
                json(
                    """{"id":7,"is4k":true,"serverId":2,"profileId":6,"rootFolder":"/tv","tags":[4],
                        "media":{"tmdbId":1399,"mediaType":"tv"}}""",
                ),
            )
            seerr.enqueue(json(SHOW_WITH_THREE_SEASONS))
            seerr.enqueue(json("""{"id":7,"media":{"tmdbId":1399,"mediaType":"tv"}}"""))

            stub.editRequest(
                EditRequestRequest
                    .newBuilder()
                    .setRequestId(7)
                    .addAllSeasonNumbers(listOf(1, 3))
                    .build(),
            )

            val read = seerr.takeRequest()
            assertEquals("GET", read.method)
            assertEquals("/api/v1/request/7", read.url.encodedPath)
            // The show's own seasons, which the edit is checked against before it goes (#1002).
            assertEquals("/api/v1/tv/1399", seerr.takeRequest().url.encodedPath)
            val update = seerr.takeRequest()
            assertEquals("PUT", update.method)
            assertEquals("/api/v1/request/7", update.url.encodedPath)
            val body = update.body?.utf8().orEmpty()
            assertTrue(body, """"mediaType":"tv"""" in body)
            assertTrue(body, """"seasons":[1,3]""" in body)
            assertTrue(body, """"is4k":true""" in body)
            // The server assigns the destination from the body, so a field left out is one it clears.
            assertTrue(body, """"serverId":2""" in body)
            assertTrue(body, """"profileId":6""" in body)
            assertTrue(body, """"rootFolder":"/tv"""" in body)
            assertTrue(body, """"tags":[4]""" in body)
        }

    @Test
    fun `an edit with no seasons, or of a movie, is INVALID_ARGUMENT`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            val before = seerr.requestCount

            assertEquals(
                Status.Code.INVALID_ARGUMENT,
                stub.code { editRequest(EditRequestRequest.newBuilder().setRequestId(7).build()) },
            )
            assertEquals(before, seerr.requestCount)

            seerr.enqueue(json("""{"id":8,"media":{"tmdbId":603,"mediaType":"movie"}}"""))
            assertEquals(
                Status.Code.INVALID_ARGUMENT,
                stub.code {
                    editRequest(
                        EditRequestRequest
                            .newBuilder()
                            .setRequestId(8)
                            .addSeasonNumbers(1)
                            .build(),
                    )
                },
            )
            assertEquals(before + 1, seerr.requestCount)
        }

    @Test
    fun `an edit naming a negative or repeated season is INVALID_ARGUMENT before anything is read`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            val before = seerr.requestCount

            listOf(listOf(1, 1), listOf(-1), (1..MAX_SEASON_NUMBERS + 1).toList()).forEach { seasons ->
                assertEquals(
                    Status.Code.INVALID_ARGUMENT,
                    stub.code {
                        editRequest(
                            EditRequestRequest
                                .newBuilder()
                                .setRequestId(7)
                                .addAllSeasonNumbers(seasons)
                                .build(),
                        )
                    },
                )
            }
            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `an edit naming a season the show does not have is INVALID_ARGUMENT, and nothing is written`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            val before = seerr.requestCount
            seerr.enqueue(json("""{"id":7,"media":{"tmdbId":1399,"mediaType":"tv"}}"""))
            seerr.enqueue(json(SHOW_WITH_THREE_SEASONS))

            assertEquals(
                Status.Code.INVALID_ARGUMENT,
                stub.code {
                    editRequest(
                        EditRequestRequest
                            .newBuilder()
                            .setRequestId(7)
                            .addAllSeasonNumbers(listOf(2, 99))
                            .build(),
                    )
                },
            )
            // The request and the show were read; no PUT followed.
            assertEquals(before + 2, seerr.requestCount)
        }

    @Test
    fun `a submit naming a repeated or negative season is INVALID_ARGUMENT, and nothing is posted`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            val before = seerr.requestCount

            listOf(listOf(2, 2), listOf(-3, 1)).forEach { seasons ->
                assertEquals(
                    Status.Code.INVALID_ARGUMENT,
                    stub.code {
                        submitRequest(
                            SubmitRequestRequest
                                .newBuilder()
                                .setMedia(show)
                                .addAllSeasonNumbers(seasons)
                                .build(),
                        )
                    },
                )
            }
            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `a submit naming a season the show does not have is INVALID_ARGUMENT, and only the details are read`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            val before = seerr.requestCount
            seerr.enqueue(json(SHOW_WITH_THREE_SEASONS))

            assertEquals(
                Status.Code.INVALID_ARGUMENT,
                stub.code {
                    submitRequest(
                        SubmitRequestRequest
                            .newBuilder()
                            .setMedia(show)
                            .addAllSeasonNumbers(listOf(2, 99))
                            .build(),
                    )
                },
            )
            assertEquals(before + 1, seerr.requestCount)
            assertEquals("/api/v1/tv/1399", seerr.takeRequest().url.encodedPath)
        }

    /** Seerr's 202 for an edit that leaves nothing to request is final, not the transient UNAVAILABLE (#1001). */
    @Test
    fun `an edit that leaves seerr nothing to request is FAILED_PRECONDITION`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            seerr.enqueue(json("""{"id":7,"media":{"tmdbId":1399,"mediaType":"tv"}}"""))
            seerr.enqueue(json(SHOW_WITH_THREE_SEASONS))
            seerr.enqueue(MockResponse(code = 202, body = """{"message":"No seasons available to request"}"""))

            assertEquals(
                Status.Code.FAILED_PRECONDITION,
                stub.code {
                    editRequest(
                        EditRequestRequest
                            .newBuilder()
                            .setRequestId(7)
                            .addSeasonNumbers(1)
                            .build(),
                    )
                },
            )
        }

    private suspend fun RequestServiceGrpcKt.RequestServiceCoroutineStub.status(media: MediaId): Status.Code =
        code { getStatus(GetStatusRequest.newBuilder().setMedia(media).build()) }

    private val show: MediaId =
        MediaId
            .newBuilder()
            .setMediaType(MediaType.MEDIA_TYPE_TV)
            .setTmdbId(1399)
            .build()

    private fun listRequest(
        filter: RequestFilter,
        pageSize: Int = 0,
        pageToken: String = "",
    ): ListRequestsRequest =
        ListRequestsRequest
            .newBuilder()
            .setFilter(filter)
            .setPageSize(pageSize)
            .setPageToken(pageToken)
            .build()

    private fun emptyPage() = json("""{"pageInfo":{"results":0},"results":[]}""")

    @Test
    fun `list requests asks seerr for a page of everything the user may see, and pages on with an opaque token`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            val before = seerr.requestCount
            seerr.enqueue(
                json(
                    """{"pageInfo":{"results":3},"results":[""" +
                        """{"id":4,"status":1,"media":{"tmdbId":603,"mediaType":"movie"},"requestedBy":{"id":2,"displayName":"Neo"}},""" +
                        """{"id":5,"status":2,"is4k":true,"media":{"tmdbId":603,"mediaType":"movie"},"requestedBy":{"id":2}}]}""",
                ),
            )
            // Both requests are for one title, so its status is read once.
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":2,"requests":[{"id":4,"status":1},{"id":5,"status":2,"is4k":true}]}}"""))

            val first = stub.listRequests(listRequest(RequestFilter.REQUEST_FILTER_ALL, pageSize = 2))

            val asked = seerr.takeRequest().url
            assertEquals("/api/v1/request", asked.encodedPath)
            assertEquals("2", asked.queryParameter("take"))
            assertEquals("0", asked.queryParameter("skip"))
            assertEquals("all", asked.queryParameter("filter"))
            assertEquals("added", asked.queryParameter("sort"))
            assertNull(asked.queryParameter("requestedBy"))
            assertEquals("/api/v1/movie/603", seerr.takeRequest().url.encodedPath)
            assertEquals(2, seerr.requestCount - before)
            assertEquals(listOf(movie, movie), first.entriesList.map { it.media })
            assertEquals(listOf(4 to false, 5 to true), first.entriesList.map { it.request.id to it.request.is4K })
            assertEquals("Neo", first.entriesList[0].request.requestedBy)
            assertEquals(Availability.AVAILABILITY_PENDING, first.entriesList[0].status.availability)
            assertEquals(2, first.entriesList[0].status.requestsCount)
            assertTrue(Capability.CAPABILITY_APPROVE in first.entriesList[0].request.allowedActionsList)
            assertTrue(first.nextPageToken.isNotEmpty())

            seerr.enqueue(
                json(
                    """{"pageInfo":{"results":3},"results":[""" +
                        """{"id":6,"status":2,"media":{"tmdbId":1399,"mediaType":"tv"},"requestedBy":{"id":1}}]}""",
                ),
            )
            seerr.enqueue(json("""{"mediaInfo":{"id":10,"status":5}}"""))

            val last = stub.listRequests(listRequest(RequestFilter.REQUEST_FILTER_ALL, pageSize = 2, pageToken = first.nextPageToken))

            assertEquals("2", seerr.takeRequest().url.queryParameter("skip"))
            assertEquals(listOf(show), last.entriesList.map { it.media })
            assertEquals(
                Availability.AVAILABILITY_AVAILABLE,
                last.entriesList
                    .single()
                    .status.availability,
            )
            assertEquals("", last.nextPageToken)
        }

    @Test
    fun `mine asks for the signed-in user's own requests by id`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            seerr.enqueue(emptyPage())

            val response = stub.listRequests(listRequest(RequestFilter.REQUEST_FILTER_MINE))

            val asked = seerr.takeRequest().url
            assertEquals("1", asked.queryParameter("requestedBy"))
            assertEquals("all", asked.queryParameter("filter"))
            assertEquals(DEFAULT_LIST_PAGE_SIZE.toString(), asked.queryParameter("take"))
            assertEquals(0, response.entriesCount)
            assertEquals("", response.nextPageToken)
        }

    @Test
    fun `recently available asks for available requests, the most recently changed first`() =
        runTest {
            val stub = connected()
            seerr.enqueue(emptyPage())

            stub.listRequests(listRequest(RequestFilter.REQUEST_FILTER_RECENTLY_AVAILABLE, pageSize = 500))

            val asked = seerr.takeRequest().url
            assertEquals("available", asked.queryParameter("filter"))
            assertEquals("modified", asked.queryParameter("sort"))
            assertEquals(MAX_LIST_PAGE_SIZE.toString(), asked.queryParameter("take"))
        }

    @Test
    fun `awaiting moderation asks a moderator's seerr for pending requests`() =
        runTest {
            val stub = connected(permissions = ADMIN)
            seerr.enqueue(emptyPage())

            stub.listRequests(listRequest(RequestFilter.REQUEST_FILTER_AWAITING_MODERATION))

            assertEquals("pending", seerr.takeRequest().url.queryParameter("filter"))
        }

    /** Seerr would answer with the requester's own pending requests, which are not waiting on them. */
    @Test
    fun `awaiting moderation is empty for a user who may not moderate, without asking seerr`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            val before = seerr.requestCount

            val nothing = stub.listRequests(listRequest(RequestFilter.REQUEST_FILTER_AWAITING_MODERATION))

            assertEquals(ListRequestsResponse.getDefaultInstance(), nothing)
            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `list requests refuses a missing filter and a page token it did not issue for this filter and page size`() =
        runTest {
            val stub = connected()
            val before = seerr.requestCount
            val mineToken = ListRequestsPageToken.issue(listRequest(RequestFilter.REQUEST_FILTER_MINE, pageSize = 10), skip = 10)

            for (request in listOf(
                listRequest(RequestFilter.REQUEST_FILTER_UNSPECIFIED),
                listRequest(RequestFilter.REQUEST_FILTER_ALL, pageToken = "not a token"),
                listRequest(RequestFilter.REQUEST_FILTER_ALL, pageSize = 10, pageToken = mineToken),
                listRequest(RequestFilter.REQUEST_FILTER_MINE, pageSize = 20, pageToken = mineToken),
                listRequest(RequestFilter.REQUEST_FILTER_ALL, pageSize = -1),
            )) {
                assertEquals(request.toString(), Status.Code.INVALID_ARGUMENT, stub.code { listRequests(request) })
            }
            assertEquals(before, seerr.requestCount)
        }

    @Test
    fun `a listed request carries what the viewer may do to it`() =
        runTest {
            val stub = connected(permissions = REQUEST)
            seerr.enqueue(
                json(
                    """{"pageInfo":{"results":1},"results":[""" +
                        """{"id":4,"status":1,"media":{"tmdbId":603,"mediaType":"movie"},"requestedBy":{"id":1}}]}""",
                ),
            )
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":2,"requests":[{"id":4,"status":1,"requestedBy":{"id":1}}]}}"""))

            val request =
                stub
                    .listRequests(listRequest(RequestFilter.REQUEST_FILTER_MINE))
                    .entriesList
                    .single()
                    .request

            assertEquals(listOf(Capability.CAPABILITY_CANCEL), request.allowedActionsList)
        }

    @Test
    fun `get statuses answers each title as get status does, a repeated one once per occurrence and read once`() =
        runTest {
            val cache = FakeStatusCache()
            cache.rows[show.mediaTypeValue to show.tmdbId] =
                CachedStatus(RequestStatus.newBuilder().setAvailability(Availability.AVAILABILITY_AVAILABLE).build(), 0L)
            val stub = connected(cache = cache)
            val before = seerr.requestCount
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":3}}"""))

            val response =
                stub.getStatuses(
                    GetStatusesRequest
                        .newBuilder()
                        .addAllMedia(listOf(movie, show, movie))
                        .build(),
                )

            assertEquals(listOf(movie, show, movie), response.statusesList.map { it.media })
            assertEquals(
                listOf(Availability.AVAILABILITY_PROCESSING, Availability.AVAILABILITY_AVAILABLE, Availability.AVAILABILITY_PROCESSING),
                response.statusesList.map { it.status.availability },
            )
            assertEquals(1, seerr.requestCount - before)
        }

    @Test
    fun `get statuses refuses more than 50 titles or an unusable one before asking seerr, and answers none with none`() =
        runTest {
            val stub = connected()
            val before = seerr.requestCount
            val tooMany = GetStatusesRequest.newBuilder().addAllMedia(List(MAX_STATUSES_PER_CALL + 1) { movie }).build()
            val unusable =
                GetStatusesRequest
                    .newBuilder()
                    .addMedia(movie)
                    .addMedia(MediaId.getDefaultInstance())
                    .build()

            assertEquals(Status.Code.INVALID_ARGUMENT, stub.code { getStatuses(tooMany) })
            assertEquals(Status.Code.INVALID_ARGUMENT, stub.code { getStatuses(unusable) })
            assertEquals(0, stub.getStatuses(GetStatusesRequest.getDefaultInstance()).statusesCount)
            assertEquals(before, seerr.requestCount)
        }

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

    /** A cache that keeps one row per title in memory, standing in for the Room one. */
    private class FakeStatusCache : MediaStatusStore {
        val rows = mutableMapOf<Pair<Int, Int>, CachedStatus>()
        var clears = 0

        override suspend fun find(media: MediaId): CachedStatus? = rows[media.mediaTypeValue to media.tmdbId]

        override suspend fun put(
            media: MediaId,
            cached: CachedStatus,
        ) {
            rows[media.mediaTypeValue to media.tmdbId] = cached
        }

        override suspend fun clearAll() {
            rows.clear()
            clears++
        }
    }

    private fun getStatus(stub: RequestServiceGrpcKt.RequestServiceCoroutineStub) =
        runBlocking { stub.getStatus(GetStatusRequest.newBuilder().setMedia(movie).build()).status }

    @Test
    fun `a status young enough for where the title is answers without asking the server`() =
        runTest {
            val cache = FakeStatusCache()
            val stub = connected(cache = cache)
            val before = seerr.requestCount
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":5}}"""))

            assertEquals(Availability.AVAILABILITY_AVAILABLE, getStatus(stub).availability)
            assertEquals(Availability.AVAILABILITY_AVAILABLE, getStatus(stub).availability)

            // One call for two lookups: nothing was enqueued for a second, and nothing asked for one.
            assertEquals(1, seerr.requestCount - before)
        }

    @Test
    fun `a row past its maximum age is refetched and rewritten`() =
        runTest {
            val cache = FakeStatusCache()
            var now = 0L
            val stub = connected(cache = cache, now = { now })
            val before = seerr.requestCount
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":3}}"""))
            assertEquals(Availability.AVAILABILITY_PROCESSING, getStatus(stub).availability)

            now = 60_000
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":5}}"""))

            assertEquals(Availability.AVAILABILITY_AVAILABLE, getStatus(stub).availability)
            assertEquals(2, seerr.requestCount - before)
            assertEquals(
                60_000,
                cache.rows.values
                    .single()
                    .fetchedAtMillis,
            )
        }

    @Test
    fun `a write drops every row, so the status after it is the server's`() =
        runTest {
            val cache = FakeStatusCache()
            val stub = connected(cache = cache)
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":2,"requests":[{"id":4,"status":1}]}}"""))
            getStatus(stub)
            seerr.takeRequest()

            seerr.enqueue(json("{}"))
            stub.approveRequest(ApproveRequestRequest.newBuilder().setRequestId(4).build())
            assertEquals(1, cache.clears)
            assertTrue(cache.rows.isEmpty())

            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":5}}"""))
            assertEquals(Availability.AVAILABILITY_AVAILABLE, getStatus(stub).availability)
        }

    /** Seerr's 409 for a request in the wrong state is the contract's FAILED_PRECONDITION, so the host refreshes (#999). */
    @Test
    fun `moderating, retrying or editing a request in the wrong state is FAILED_PRECONDITION`() =
        runTest {
            val stub = connected(permissions = ADMIN)

            seerr.enqueue(MockResponse(code = 409, body = """{"message":"Only pending requests can be approved or declined."}"""))
            assertEquals(
                Status.Code.FAILED_PRECONDITION,
                stub.code { approveRequest(ApproveRequestRequest.newBuilder().setRequestId(4).build()) },
            )
            seerr.enqueue(MockResponse(code = 409, body = """{"message":"Only failed requests can be retried."}"""))
            assertEquals(
                Status.Code.FAILED_PRECONDITION,
                stub.code { retryRequest(RetryRequestRequest.newBuilder().setRequestId(4).build()) },
            )
            seerr.enqueue(json("""{"id":4,"media":{"tmdbId":1399,"mediaType":"tv"}}"""))
            seerr.enqueue(MockResponse(code = 409, body = """{"message":"Only pending requests can be modified."}"""))
            assertEquals(
                Status.Code.FAILED_PRECONDITION,
                stub.code {
                    editRequest(
                        EditRequestRequest
                            .newBuilder()
                            .setRequestId(4)
                            .addSeasonNumbers(1)
                            .build(),
                    )
                },
            )
        }

    /** The contract defines them as what this user may do *now*, so a row that stored them would lie. */
    @Test
    fun `allowed actions are recomputed on every read and never stored`() =
        runTest {
            val cache = FakeStatusCache()
            val stub = connected(cache = cache)
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":5,"requests":[{"id":4,"status":2}]}}"""))
            getStatus(stub)

            assertTrue(
                cache.rows.values
                    .single()
                    .status.allowedActionsList
                    .isEmpty(),
            )
            assertTrue(Capability.CAPABILITY_REPORT_ISSUE in getStatus(stub).allowedActionsList)
        }

    /** #443: a plain requester is offered a cancel only on their own pending request, from the cache as from the server. */
    @Test
    fun `cancel is offered per request, on the viewer's own pending one`() =
        runTest {
            val cache = FakeStatusCache()
            val stub = connected(permissions = REQUEST, cache = cache)
            seerr.enqueue(
                json(
                    """{"mediaInfo":{"id":9,"status":2,"requests":[""" +
                        """{"id":4,"status":1,"requestedBy":{"id":1}},{"id":6,"status":1,"requestedBy":{"id":2}}]}}""",
                ),
            )

            listOf(getStatus(stub), getStatus(stub)).forEach { status ->
                val byId = status.requestsList.associate { it.id to it.allowedActionsList }
                assertEquals(listOf(Capability.CAPABILITY_CANCEL), byId[4])
                assertEquals(emptyList<Capability>(), byId[6])
                assertTrue(Capability.CAPABILITY_CANCEL in status.allowedActionsList)
            }
            assertEquals(
                mapOf(4 to 1, 6 to 2),
                cache.rows.values
                    .single()
                    .requesterIds,
            )
        }

    /** The stream is what keeps the row warm; a poll answering from the row it wrote would never see the server. */
    @Test
    fun `observe may answer its first push from the cache and never its later ones`() =
        runTest {
            val cache = FakeStatusCache()
            val stub = connected(cache = cache)
            val before = seerr.requestCount
            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":5}}"""))
            getStatus(stub)
            assertEquals(1, seerr.requestCount - before)

            seerr.enqueue(json("""{"mediaInfo":{"id":9,"status":4}}"""))
            val pushed =
                stub
                    .observeStatus(ObserveStatusRequest.newBuilder().setMedia(movie).build())
                    .take(2)
                    .toList()
                    .map { it.status.availability }

            assertEquals(
                listOf(Availability.AVAILABILITY_AVAILABLE, Availability.AVAILABILITY_PARTIALLY_AVAILABLE),
                pushed,
            )
        }

    private fun json(body: String): MockResponse =
        MockResponse(code = 200, headers = okhttp3.Headers.headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }

    private class RecordingBingeConnectionStore : BingeConnectionStore {
        var recorded = false

        override val hasConnected = kotlinx.coroutines.flow.flowOf(recorded)

        override val dismissedHints = kotlinx.coroutines.flow.flowOf(emptySet<io.github.scottcooper92.binge.seerr.auth.BingeHint>())

        override suspend fun dismissHint(hint: io.github.scottcooper92.binge.seerr.auth.BingeHint) = Unit

        override suspend fun recordHandshake() {
            recorded = true
        }

        override suspend fun forget() {
            recorded = false
        }
    }
}
