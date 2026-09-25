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
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.IssueType
import com.binge.companion.contracts.request.v1.ObserveAttentionRequest
import com.binge.companion.contracts.request.v1.ObserveStatusRequest
import com.binge.companion.contracts.request.v1.ReportIssueRequest
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.SubmitAdvancedRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.UnblockTitleRequest
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.CachedStatus
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
import io.github.scottcooper92.binge.seerr.data.NoMediaStatusStore
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
private const val REQUEST_ADVANCED = 1 shl 13
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

    private fun connected(
        permissions: Int = ADMIN,
        version: String = "2.7.0",
        cache: MediaStatusStore = NoMediaStatusStore,
        now: () -> Long = { 0L },
    ): RequestServiceGrpcKt.RequestServiceCoroutineStub {
        val store =
            CredentialStore(
                dataStore = PreferenceDataStoreFactory.create { folder.newFile("creds.preferences_pb") },
                cipher = PlainCipher,
            )
        runBlocking { store.save(SeerrCredentials(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"), SeerrVariant.fromVersion(version))) }
        val connection = SeerrConnection(store, SeerrApiFactory(logRequests = false))
        seerr.enqueue(json("""{"version":"$version"}"""))
        seerr.enqueue(json("""{"initialized":true}"""))
        seerr.enqueue(json("""{"id":1,"permissions":$permissions}"""))
        val stub =
            serve(
                SeerrRequestService(
                    connection,
                    versionName = "0.1.0-test",
                    clock = now,
                    observeIntervalMillis = 1,
                    attentionIntervalMillis = 1,
                    statusCache = cache,
                ),
            )
        // One handshake up front consumes the profile's two answers and `auth/me` and caches all
        // three, so each test's recorded requests are its own rather than starting with lookups.
        runBlocking { stub.handshake(HandshakeRequest.getDefaultInstance()) }
        repeat(3) { seerr.takeRequest() }
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
                setOf(
                    Capability.CAPABILITY_OBSERVE_STATUS,
                    Capability.CAPABILITY_ATTENTION,
                    Capability.CAPABILITY_CANCEL,
                    Capability.CAPABILITY_EDIT_SEASONS,
                    Capability.CAPABILITY_REPORT_ISSUE,
                ),
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
    fun `overseerr has no blocklist, so an admin there is not offered the block capability`() =
        runTest {
            val response = connected(permissions = ADMIN, version = "1.33.2").handshake(HandshakeRequest.getDefaultInstance())

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

    @Test
    fun `an unblock deletes the title's blocklist entry by tmdb id and media type`() =
        runTest {
            val stub = connected(version = "3.1.0")
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

            assertEquals("/api/v1/blacklist/603", seerr.takeRequest().url.encodedPath)
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
    fun `advanced options lists every server for the shape, preselected on the plain non-4k default`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)

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
    fun `destination options re-resolves profile and root folder for the server the host moved to`() =
        runTest {
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
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
            val stub = connected(permissions = REQUEST or REQUEST_ADVANCED)
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
            // is4k is the default (false), and the Json config used for the body doesn't encode defaults.
            assertFalse(posted.contains("\"is4k\""))
            assertTrue(posted.contains("\"serverId\":1"))
            assertTrue(posted.contains("\"profileId\":4"))
            assertTrue(posted.contains("\"rootFolder\":\"/media\""))
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
}
