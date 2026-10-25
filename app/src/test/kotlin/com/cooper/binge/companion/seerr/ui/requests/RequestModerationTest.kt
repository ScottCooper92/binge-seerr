package com.cooper.binge.companion.seerr.ui.requests

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.FakeRequestStore
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import com.cooper.binge.companion.seerr.util.RecordingCrashBreadcrumbs
import com.cooper.binge.companion.seerr.util.awaitEvent
import com.cooper.binge.companion.seerr.util.requestItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList

private const val SYNC_WAIT_MILLIS = 2_000L
private const val POLL_MILLIS = 10L

/** The write side over a real connection into a path-scripted Seerr, one that still serves the blocklist at its old path. */
class RequestModerationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = CopyOnWriteArrayList<RecordedRequest>()
    private val codes = mutableMapOf<String, Int>()
    private val bodies = mutableMapOf<String, String>()
    private var moderated = 0
    private val cache = FakeRequestStore()
    private val analytics = RecordingAnalytics()
    private val crashBreadcrumbs = RecordingCrashBreadcrumbs()

    @After
    fun tearDown() = seerr.close()

    private suspend fun TestScope.moderation(): RequestModeration {
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    val path = request.url.encodedPath
                    val body =
                        when (path) {
                            "/api/v1/auth/me" -> """{"id":1,"permissions":2}"""
                            "/api/v1/status" -> """{"version":"2.7.0"}"""
                            "/api/v1/settings/public" -> """{"mediaServerType":2}"""
                            else -> bodies[path] ?: "{}"
                        }
                    return MockResponse(code = codes[path] ?: 200, headers = headersOf("Content-Type", "application/json"), body = body)
                }
            }
        seerr.start()
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("m.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        return RequestModeration(
            scope = backgroundScope,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            connection = connection,
            analytics = analytics,
            crashBreadcrumbs = crashBreadcrumbs,
            cache = cache,
        ) { moderated++ }
    }

    private val item =
        requestItem(
            id = 11,
            tmdbId = 550,
            title = "Fight Club",
            year = "1999",
            requestedBy = "scott",
            requestedById = 7,
            status = SeerrRequestStatusCode(1),
        )

    @Test
    fun `approving posts, reports, and tells the owner to refresh`() =
        runTest {
            val sut = moderation()

            val approved = awaitEvent(sut.events)
            sut.approve(11)

            assertEquals(ModerationEvent.Approved, approved.await())
            assertEquals("/api/v1/request/11/approve", received.last { it.method == "POST" }.url.encodedPath)
            assertEquals(1, moderated)
            assertTrue(sut.actingIds.value.isEmpty())
            assertEquals(listOf("request_moderated" to mapOf("action" to "approved")), analytics.events)
            assertEquals(listOf("moderating request: approved"), crashBreadcrumbs.logs)
            assertEquals(listOf("request_id" to "11"), crashBreadcrumbs.keys)
        }

    @Test
    fun `deleting files with requests deletes the files first, then removes each request and its cached row`() =
        runTest {
            cache.append("all:added:all", listOf(item.toEntity("all:added:all", 0), item.copy(id = 12).toEntity("all:added:all", 1)), null)
            val sut = moderation()
            var filesHeard = 0

            val done = awaitEvent(sut.events)
            sut.deleteMediaFiles(11, 900, is4k = false, removeRequestIds = listOf(11, 12)) { filesHeard++ }

            val event = done.await()
            assertEquals(ModerationEvent.MediaFilesAndRequestsDeleted(removedThisRequest = true), event)
            assertTrue(event.removesTheRequest)
            val deletes = received.filter { it.method == "DELETE" }.map { it.url.encodedPath }
            assertEquals(listOf("/api/v1/media/900/file", "/api/v1/request/11", "/api/v1/request/12"), deletes)
            assertEquals(1, filesHeard)
            assertEquals(1, moderated)
            assertTrue(cache.rows.isEmpty())
        }

    @Test
    fun `files deleted with only another request removed leave the page, and a removal that fails says so`() =
        runTest {
            val sut = moderation()

            val sibling = awaitEvent(sut.events)
            sut.deleteMediaFiles(11, 900, is4k = true, removeRequestIds = listOf(12))
            val event = sibling.await()
            assertEquals(ModerationEvent.MediaFilesAndRequestsDeleted(removedThisRequest = false), event)
            assertTrue(!event.removesTheRequest)
            assertEquals("true", received.first { it.url.encodedPath == "/api/v1/media/900/file" }.url.queryParameter("is4k"))

            codes["/api/v1/request/12"] = 500
            var filesHeard = 0
            val kept = awaitEvent(sut.events)
            sut.deleteMediaFiles(11, 900, is4k = false, removeRequestIds = listOf(12)) { filesHeard++ }
            assertEquals(ModerationEvent.MediaFilesDeletedRequestsKept(removedThisRequest = false), kept.await())
            assertEquals(1, filesHeard)

            val own = awaitEvent(sut.events)
            sut.deleteMediaFiles(11, 900, is4k = false, removeRequestIds = listOf(11, 12))
            val ownEvent = own.await()
            assertEquals(ModerationEvent.MediaFilesDeletedRequestsKept(removedThisRequest = true), ownEvent)
            assertTrue(ownEvent.removesTheRequest)

            codes["/api/v1/media/900/file"] = 500
            val failed = awaitEvent(sut.events)
            sut.deleteMediaFiles(11, 900, is4k = false, removeRequestIds = listOf(12)) { filesHeard++ }
            assertTrue(failed.await() is ModerationEvent.Failed)
            assertEquals("a refused deletion removes no request", 1, filesHeard)
            assertEquals(3, received.count { it.method == "DELETE" && it.url.encodedPath == "/api/v1/request/12" })
        }

    /**
     * Seerr answers 409 when no Radarr or Sonarr is its default for the version (#1726): a setting, not a stale target. The
     * page hears it in place, nothing is removed or reloaded, and the report names the reason. A 409 on approve is unchanged.
     */
    @Test
    fun `a 409 for want of a default server is its own refusal, and a 409 on approve is still a rejection`() =
        runTest {
            codes["/api/v1/media/900/file"] = 409
            bodies["/api/v1/media/900/file"] = """{"message":"No 4K Radarr server configured to delete media files"}"""
            val sut = moderation()
            val heard = mutableListOf<FilesDeletion>()

            val refused = awaitEvent(sut.events)
            sut.deleteMediaFiles(11, 900, is4k = true, removeRequestIds = listOf(11)) { heard += it }

            assertEquals(ModerationEvent.MediaFilesNoDefaultServer, refused.await())
            assertEquals(listOf(FilesDeletion.NoDefaultServer), heard)
            assertEquals(listOf("/api/v1/media/900/file"), received.filter { it.method == "DELETE" }.map { it.url.encodedPath })
            assertEquals(0, moderated)
            assertTrue(sut.actingIds.value.isEmpty())

            codes["/api/v1/request/12/approve"] = 409
            bodies["/api/v1/request/12/approve"] = """{"message":"Only pending requests can be approved or declined."}"""
            val rejected = awaitEvent(sut.events)
            sut.approve(12)
            assertEquals(ModerationEvent.Failed(SeerrError.Rejected), rejected.await())

            fun failure(
                operation: String,
                reason: String? = null,
            ) = "request_operation_failed" to
                mapOf(
                    "operation" to operation,
                    "http_status" to "409",
                    "grpc_status" to "FAILED_PRECONDITION",
                    "server_lineage" to "jellyseerr",
                    "server_version" to "2.7.0",
                ) + listOfNotNull(reason?.let { "reason" to it })
            assertEquals(
                listOf(failure("moderate_media_files_and_requests_deleted", "no_default_service"), failure("moderate_approved")),
                analytics.events,
            )
        }

    @Test
    fun `a 409 on delete files without Seerr's words is the plain failure`() =
        runTest {
            codes["/api/v1/media/900/file"] = 409
            val sut = moderation()
            val heard = mutableListOf<FilesDeletion>()

            val failed = awaitEvent(sut.events)
            sut.deleteMediaFiles(11, 900, is4k = false, removeRequestIds = emptyList()) { heard += it }

            assertEquals(ModerationEvent.Failed(SeerrError.Rejected), failed.await())
            assertTrue(heard.isEmpty())
        }

    @Test
    fun `approving, declining and removing move the cached row, and a failed write leaves it`() =
        runTest {
            cache.append("all:added:all", listOf(item.toEntity("all:added:all", 0), item.copy(id = 12).toEntity("all:added:all", 1)), null)
            val sut = moderation()

            codes["/api/v1/request/11/approve"] = 500
            val failed = awaitEvent(sut.events)
            sut.approve(11)
            failed.await()
            assertEquals(1, cache.rows.first { it.id == 11 }.status)

            codes.clear()
            val approved = awaitEvent(sut.events)
            sut.approve(11)
            approved.await()
            assertEquals(SeerrRequestStatusCode.Approved.raw, cache.rows.first { it.id == 11 }.status)

            val declined = awaitEvent(sut.events)
            sut.decline(item, blockTitle = false)
            declined.await()
            assertEquals(SeerrRequestStatusCode.Declined.raw, cache.rows.first { it.id == 11 }.status)

            val removed = awaitEvent(sut.events)
            sut.remove(item, blockTitle = false)
            removed.await()
            assertEquals(listOf(12), cache.rows.map { it.id })
        }

    @Test
    fun `a 4K request writes the 4K media status into the cache, not the regular one`() =
        runTest {
            val row = item.copy(is4k = true, mediaStatus = SeerrMediaStatusCode.Unknown)
            cache.append("all:added:all", listOf(row.toEntity("all:added:all", 0)), null)
            val sut = moderation()

            bodies["/api/v1/request/11"] =
                """{"id":11,"status":2,"is4k":true,"media":{"mediaType":"movie","tmdbId":550,"status":5,"status4k":3}}"""
            val approved = awaitEvent(sut.events)
            sut.approve(11)
            approved.await()

            withContext(Dispatchers.Default) {
                withTimeout(SYNC_WAIT_MILLIS) {
                    while (cache.rows.first().mediaStatus != SeerrMediaStatusCode.Processing.raw) delay(POLL_MILLIS)
                }
            }
        }

    @Test
    fun `a request the server no longer has is dropped from the cache once the action lands, and its counts are re-read`() =
        runTest {
            cache.append("all:added:all", listOf(item.toEntity("all:added:all", 0)), null)
            val sut = moderation()

            codes["/api/v1/request/11"] = 404
            val cleared = awaitEvent(sut.events)
            sut.clearMedia(11, 900)
            cleared.await()

            // The cache sync follows the event on the network, so wait for it in real time.
            withContext(Dispatchers.Default) {
                withTimeout(SYNC_WAIT_MILLIS) {
                    while (cache.rows.isNotEmpty() || received.none { it.url.encodedPath == "/api/v1/request/count" }) delay(POLL_MILLIS)
                }
            }
        }

    @Test
    fun `declining with a block declines, then posts the title to the lineage's blocklist path`() =
        runTest {
            val sut = moderation()

            val declinedAndBlocked = awaitEvent(sut.events)
            sut.decline(item, blockTitle = true)

            assertEquals(ModerationEvent.DeclinedAndBlocked, declinedAndBlocked.await())
            val posts = received.filter { it.method == "POST" }.map { it.url.encodedPath }
            assertEquals(listOf("/api/v1/request/11/decline", "/api/v1/blacklist"), posts.takeLast(2))
            val block =
                received
                    .last { it.method == "POST" }
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue(block.contains("\"tmdbId\":550"))
            assertTrue(block.contains("\"mediaType\":\"movie\""))
            assertTrue(block.contains("\"user\":1"))
            assertEquals(
                listOf("request_moderated" to mapOf("action" to "declined_and_blocked")),
                analytics.events,
            )
        }

    @Test
    fun `blocking the title on its own posts to the blocklist and leaves the request, and a failure says so`() =
        runTest {
            val sut = moderation()

            val blocked = awaitEvent(sut.events)
            sut.blockTitle(item)

            assertEquals(ModerationEvent.Blocked, blocked.await())
            val posts = received.filter { it.method == "POST" }.map { it.url.encodedPath }
            assertEquals(listOf("/api/v1/blacklist"), posts)
            assertEquals(listOf("request_moderated" to mapOf("action" to "blocked")), analytics.events)

            codes["/api/v1/blacklist"] = 500
            val failed = awaitEvent(sut.events)
            sut.blockTitle(item)
            assertEquals(ModerationEvent.BlockFailed, failed.await())
        }

    /**
     * Seerr refuses a second add: 412 on SQLite, 409 on Postgres. The title is blocked either way, so the console says
     * so, as the exported Service does (#1139, #1000).
     */
    @Test
    fun `blocking a title already on the blocklist is a block, on either database`() =
        runTest {
            val sut = moderation()

            for (code in listOf(412, 409)) {
                codes["/api/v1/blacklist"] = code
                val blocked = awaitEvent(sut.events)
                sut.blockTitle(item)
                assertEquals(ModerationEvent.Blocked, blocked.await())
            }
            assertEquals(2, moderated)

            codes["/api/v1/blacklist"] = 412
            val declinedAndBlocked = awaitEvent(sut.events)
            sut.decline(item, blockTitle = true)
            assertEquals(ModerationEvent.DeclinedAndBlocked, declinedAndBlocked.await())
        }

    @Test
    fun `a block that fails after the removal landed is its own outcome, and a rejected action carries its error`() =
        runTest {
            codes["/api/v1/blacklist"] = 500
            val sut = moderation()

            val removedButBlockFailed = awaitEvent(sut.events)
            sut.remove(item, blockTitle = true)
            assertEquals(ModerationEvent.RemovedButBlockFailed, removedButBlockFailed.await())
            assertEquals("DELETE", received.first { it.url.encodedPath == "/api/v1/request/11" }.method)
            assertEquals(1, moderated)

            // An expired sign-in: auth/me refuses the interceptor's probe too, so the 401 is the session (#997).
            codes["/api/v1/request/12/approve"] = 401
            codes["/api/v1/auth/me"] = 401
            val failed = awaitEvent(sut.events)
            sut.approve(12)
            assertEquals(ModerationEvent.Failed(SeerrError.Unauthorized), failed.await())
            assertEquals(1, moderated)
            // The 500 on the block names a server fault and is reported; the 401 is an expired sign-in and is not.
            assertEquals(
                listOf(
                    "request_operation_failed" to
                        mapOf(
                            "operation" to "block_title",
                            "http_status" to "500",
                            "grpc_status" to "UNAVAILABLE",
                            "server_lineage" to "jellyseerr",
                            "server_version" to "2.7.0",
                        ),
                    "request_moderated" to mapOf("action" to "removed_block_failed"),
                ),
                analytics.events,
            )
        }
}
