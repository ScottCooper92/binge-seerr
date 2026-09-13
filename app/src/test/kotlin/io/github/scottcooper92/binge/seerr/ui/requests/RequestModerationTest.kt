package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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

/** The write side over a real connection into a path-scripted Seerr, one that still serves the blocklist at its old path. */
class RequestModerationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = mutableListOf<RecordedRequest>()
    private val codes = mutableMapOf<String, Int>()
    private var moderated = 0

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
                            else -> "{}"
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
        return RequestModeration(scope = backgroundScope, connection = connection) { moderated++ }
    }

    private val item =
        RequestItem(
            id = 11,
            tmdbId = 550,
            mediaType = RequestMediaType.Movie,
            title = "Fight Club",
            posterUrl = null,
            year = "1999",
            requestedBy = "scott",
            requestedById = 7,
            requestedAtMillis = null,
            status = SeerrRequestStatusCode(1),
            mediaStatus = null,
            download = null,
            seasonNumbers = emptyList(),
            is4k = false,
        )

    @Test
    fun `approving posts, reports, and tells the owner to refresh`() =
        runTest {
            val sut = moderation()

            sut.approve(11)

            assertEquals(ModerationEvent.Approved, sut.events.first())
            assertEquals("/api/v1/request/11/approve", received.last { it.method == "POST" }.url.encodedPath)
            assertEquals(1, moderated)
            assertTrue(sut.actingIds.value.isEmpty())
        }

    @Test
    fun `declining with a block declines, then posts the title to the lineage's blocklist path`() =
        runTest {
            val sut = moderation()

            sut.decline(item, blockTitle = true)

            assertEquals(ModerationEvent.DeclinedAndBlocked, sut.events.first())
            val posts = received.filter { it.method == "POST" }.map { it.url.encodedPath }
            assertEquals(listOf("/api/v1/request/11/decline", "/api/v1/blacklist"), posts.takeLast(2))
            val block =
                received
                    .last()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue(block.contains("\"tmdbId\":550"))
            assertTrue(block.contains("\"mediaType\":\"movie\""))
        }

    @Test
    fun `a block that fails after the removal landed is its own outcome, and a rejected action carries its error`() =
        runTest {
            codes["/api/v1/blacklist"] = 500
            val sut = moderation()

            sut.remove(item, blockTitle = true)
            assertEquals(ModerationEvent.RemovedButBlockFailed, sut.events.first())
            assertEquals("DELETE", received.first { it.url.encodedPath == "/api/v1/request/11" }.method)
            assertEquals(1, moderated)

            codes["/api/v1/request/12/approve"] = 401
            sut.approve(12)
            assertEquals(ModerationEvent.Failed(SeerrError.Unauthorized), sut.events.first())
            assertEquals(1, moderated)
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
