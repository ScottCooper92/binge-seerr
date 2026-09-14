package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import io.github.scottcooper92.binge.seerr.seerr.SEERR_MEDIA_TYPE_TV
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaDetailsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaInfoDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestMediaDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrSeasonDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrSeasonStatusDto
import io.github.scottcooper92.binge.seerr.ui.Choice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList

/** Radarr answers for a film and Sonarr for a series; the editor picks by the request's own media type. */
private const val RADARR = "/api/v1/service/radarr"
private const val SONARR = "/api/v1/service/sonarr"

private val SERVERS =
    """[{"id":1,"name":"Main","isDefault":true,"activeProfileId":7,"activeDirectory":"/films"},
       {"id":2,"name":"Spare","activeProfileId":9,"activeDirectory":"/spare"},
       {"id":3,"name":"4K","is4k":true}]"""

/** What the `PUT` answers with; the editor only needs it to decode, not to read anything off it. */
private val EDITED = """{"id":11,"media":{"tmdbId":550,"mediaType":"movie"}}"""

private val SERVER_DETAILS =
    """{"server":{"id":1,"name":"Main"},"profiles":[{"id":7,"name":"HD"},{"id":8,"name":"SD"}],
       "rootFolders":[{"id":1,"path":"/films"},{"id":2,"path":"/other"}],
       "tags":[{"id":4,"label":"kids"},{"id":5,"label":"archive"}]}"""

/** The editor for one request, over a real connection into a path-scripted Seerr. */
class RequestEditorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = CopyOnWriteArrayList<RecordedRequest>()

    /** Paths the dispatcher should answer 500 for, so a failed load can be driven. */
    private val failing = mutableSetOf<String>()

    @After
    fun tearDown() = seerr.close()

    private suspend fun TestScope.editor(): RequestEditor {
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    val path = request.url.encodedPath
                    if (path in failing) return MockResponse(code = 500)
                    val body =
                        when {
                            path == "/api/v1/auth/me" -> """{"id":1,"permissions":2}"""
                            path == "/api/v1/status" -> """{"version":"2.7.0"}"""
                            path == "/api/v1/settings/public" -> """{"mediaServerType":2}"""
                            path == RADARR || path == SONARR -> SERVERS
                            path.startsWith("$RADARR/") || path.startsWith("$SONARR/") -> SERVER_DETAILS
                            path.startsWith("/api/v1/request/") -> EDITED
                            else -> "{}"
                        }
                    return MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
                }
            }
        seerr.start()
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("e.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val moderation = RequestModeration(scope = backgroundScope, connection = connection) {}
        return RequestEditor(scope = backgroundScope, connection = connection, moderation = moderation)
    }

    private suspend fun RequestEditor.awaitLoaded(): EditState =
        state.first { it != null && it.destination?.loadingChoices != true } as EditState

    /** The editor closes on the moderation's `Edited`, so this is the save having gone through. */
    private suspend fun RequestEditor.awaitClosed() = state.first { it == null }

    private fun paths(prefix: String) = received.filter { it.url.encodedPath.startsWith(prefix) }.map { it.url.encodedPath }

    private fun editBody() =
        Json
            .parseToJsonElement(
                received
                    .last { it.method == "PUT" }
                    .body
                    ?.utf8()
                    .orEmpty(),
            ).jsonObject

    @Test
    fun `a film's destination comes from Radarr, and its saved body carries no seasons`() =
        runTest {
            val editor = editor()
            editor.start(EditSource(movieRequest(), details = null, canEditDestination = true))

            val loaded = editor.awaitLoaded()
            assertEquals(listOf(RADARR, "$RADARR/1"), paths(RADARR))
            assertTrue(paths(SONARR).isEmpty())
            // The 4K instance is not offered: this request is not 4K, and Seerr keeps the two apart.
            assertEquals(listOf(1, 2), loaded.destination?.servers?.map { it.id })
            assertFalse(loaded.seasonsUnknown)
            assertTrue(loaded.canSave)

            editor.save()
            editor.awaitClosed()

            val body = editBody()
            assertNull(body["seasons"])
            assertEquals(SEERR_MEDIA_TYPE_MOVIE, body.getValue("mediaType").jsonPrimitive.content)
            assertEquals(1, body.getValue("serverId").jsonPrimitive.int)
            assertEquals(7, body.getValue("profileId").jsonPrimitive.int)
            assertEquals("/films", body.getValue("rootFolder").jsonPrimitive.content)
        }

    @Test
    fun `a series' destination comes from Sonarr, and its saved body carries the ticked seasons`() =
        runTest {
            val editor = editor()
            editor.start(EditSource(tvRequest(), details = showDetails(), canEditDestination = true))

            val loaded = editor.awaitLoaded()
            assertEquals(listOf(SONARR, "$SONARR/1"), paths(SONARR))
            assertTrue(paths(RADARR).isEmpty())
            // Season 1 is this request's own and starts ticked; season 3 is held by the server, so it is locked.
            assertEquals(listOf(1, 2, 3), loaded.seasons.map { it.number })
            assertEquals(listOf(true, false, false), loaded.seasons.map { it.selected })
            assertEquals(listOf(false, false, true), loaded.seasons.map { it.locked })

            editor.toggleSeason(2)
            editor.toggleSeason(3)
            editor.save()
            editor.awaitClosed()

            // A locked season is neither untickable nor sent: only 1 and 2 go.
            assertEquals(listOf(1, 2), editBody().getValue("seasons").jsonArray.map { it.jsonPrimitive.int })
        }

    @Test
    fun `a series whose details did not load sends no seasons, and cannot be saved at all`() =
        runTest {
            val editor = editor()
            editor.start(EditSource(tvRequest(), details = null, canEditDestination = false))

            val state = editor.awaitLoaded()
            assertTrue(state.seasonsUnknown)
            assertTrue(state.seasons.isEmpty())
            // Saving the whole new season set is what the PUT does, so an empty one would drop every season.
            assertFalse(state.canSave)

            editor.save()
            assertTrue(received.none { it.method == "PUT" })
        }

    @Test
    fun `choosing another server re-reads its choices and drops the tags the first one had`() =
        runTest {
            val editor = editor()
            editor.start(EditSource(movieRequest(tags = listOf(4)), details = null, canEditDestination = true))
            assertEquals(setOf(4), editor.awaitLoaded().destination?.tagIds)

            editor.selectServer(2)
            val moved = editor.awaitLoaded()

            assertEquals(listOf(RADARR, "$RADARR/1", "$RADARR/2"), paths(RADARR))
            assertEquals(2, moved.destination?.serverId)
            assertEquals(emptySet<Int>(), moved.destination?.tagIds)
        }

    @Test
    fun `a server list that will not load leaves the picker empty rather than waiting forever`() =
        runTest {
            failing += RADARR
            val editor = editor()
            editor.start(EditSource(movieRequest(), details = null, canEditDestination = true))

            val loaded = editor.awaitLoaded()
            assertEquals(emptyList<Choice>(), loaded.destination?.servers)
            assertTrue(loaded.canSave)
        }

    private fun movieRequest(tags: List<Int> = emptyList()) =
        SeerrRequestDto(
            id = 11,
            media = SeerrRequestMediaDto(tmdbId = 550, mediaType = SEERR_MEDIA_TYPE_MOVIE),
            tags = tags,
        )

    private fun tvRequest() =
        SeerrRequestDto(
            id = 12,
            media = SeerrRequestMediaDto(tmdbId = 1399, mediaType = SEERR_MEDIA_TYPE_TV),
            seasons = listOf(SeerrSeasonStatusDto(seasonNumber = 1)),
        )

    /** Specials and a season with no episodes are left out; season 3 is already on the server. */
    private fun showDetails() =
        SeerrMediaDetailsDto(
            seasons =
                listOf(
                    SeerrSeasonDto(seasonNumber = 0, name = "Specials", episodeCount = 2),
                    SeerrSeasonDto(seasonNumber = 1, name = "Season 1", episodeCount = 10),
                    SeerrSeasonDto(seasonNumber = 2, name = "Season 2", episodeCount = 10),
                    SeerrSeasonDto(seasonNumber = 3, name = "Season 3", episodeCount = 10),
                    SeerrSeasonDto(seasonNumber = 4, name = "Season 4", episodeCount = 0),
                ),
            mediaInfo =
                SeerrMediaInfoDto(
                    seasons = listOf(SeerrSeasonStatusDto(seasonNumber = 3, status = SeerrMediaStatusCode.Available)),
                ),
        )

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
