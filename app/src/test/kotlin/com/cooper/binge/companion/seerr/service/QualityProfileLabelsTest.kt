package com.cooper.binge.companion.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.QualityLabels
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrDestination
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val RADARR_ONE = "/api/v1/service/radarr/1"
private const val HOUR_MILLIS = 60 * 60 * 1000L
private const val MINUTE_MILLIS = 60 * 1000L

private const val RADARR_ONE_PROFILES =
    """{"server":{"id":1,"name":"Radarr"},"profiles":[{"id":4,"name":"HD-1080p"},{"id":7,"name":"HD-720p"}]}"""

class QualityProfileLabelsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val answers = mutableMapOf<String, () -> MockResponse>()
    private val hits = mutableListOf<String>()
    private var now = 0L
    private var stores = 0

    private val seerr =
        MockWebServer().apply {
            dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.url.encodedPath
                        hits += path
                        return answers[path]?.invoke() ?: MockResponse
                            .Builder()
                            .code(404)
                            .body("{}")
                            .build()
                    }
                }
            start()
        }

    /** Reads of the one server's profile list; a refused call may also make the connection re-check the session. */
    private fun profileReads() = hits.count { it == RADARR_ONE }

    @After
    fun tearDown() = seerr.close()

    private fun labels(): QualityProfileLabels {
        val store =
            CredentialStore(
                dataStore = PreferenceDataStoreFactory.create { folder.newFile("creds${stores++}.preferences_pb") },
                cipher = PlainCipher,
                io = Dispatchers.Unconfined,
            )
        runBlocking { store.save(SeerrCredentials(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"), SeerrVariant.fromVersion("2.7.0"))) }
        return QualityProfileLabels(SeerrConnection(store, SeerrApiFactory(logRequests = false)), clock = { now })
    }

    private fun answer(
        path: String,
        code: Int = 200,
        body: String = "{}",
    ) {
        answers[path] = {
            MockResponse
                .Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build()
        }
    }

    private fun QualityLabels.of(
        profileId: Int?,
        serverId: Int? = 1,
        isTv: Boolean = false,
    ) = labelOf(SeerrDestination(isTv, serverId, profileId))

    private fun destination(
        profileId: Int?,
        serverId: Int? = 1,
        isTv: Boolean = false,
    ) = SeerrDestination(isTv, serverId, profileId)

    @Test
    fun `a request's profile id is named from its server's profile list`() =
        runTest {
            answer(RADARR_ONE, body = RADARR_ONE_PROFILES)

            val lookup = labels().forDestinations(listOf(destination(7)))

            assertEquals("HD-720p", lookup.of(7))
        }

    @Test
    fun `a profile the server no longer lists has no label`() =
        runTest {
            answer(RADARR_ONE, body = RADARR_ONE_PROFILES)

            val lookup = labels().forDestinations(listOf(destination(99)))

            assertNull(lookup.of(99))
        }

    @Test
    fun `a request missing either id has no label and asks the server nothing`() =
        runTest {
            answer(RADARR_ONE, body = RADARR_ONE_PROFILES)

            val lookup = labels().forDestinations(listOf(destination(profileId = null), destination(profileId = 7, serverId = null)))

            assertNull(lookup.of(null))
            assertNull(lookup.of(7, serverId = null))
            assertEquals(emptyList<String>(), hits)
        }

    @Test
    fun `an unknown server has no label`() =
        runTest {
            val lookup = labels().forDestinations(listOf(destination(7, serverId = 5)))

            assertNull(lookup.of(7, serverId = 5))
        }

    @Test
    fun `a refused or failing read is a missing label, not an error`() =
        runTest {
            val unauthorised = labels()
            answer(RADARR_ONE, code = 403)
            assertNull(unauthorised.forDestinations(listOf(destination(7))).of(7))

            answer(RADARR_ONE, code = 500)
            assertNull(labels().forDestinations(listOf(destination(7))).of(7))

            answer(RADARR_ONE, body = "not json")
            assertNull(labels().forDestinations(listOf(destination(7))).of(7))
        }

    @Test
    fun `one read serves every row for a server`() =
        runTest {
            answer(RADARR_ONE, body = RADARR_ONE_PROFILES)
            val labels = labels()

            val first = labels.forDestinations(listOf(destination(4), destination(7), destination(4)))
            val second = labels.forDestinations(listOf(destination(7)))

            assertEquals(listOf("HD-1080p", "HD-720p"), listOf(first.of(4), first.of(7)))
            assertEquals("HD-720p", second.of(7))
            assertEquals(1, profileReads())
        }

    @Test
    fun `radarr and sonarr servers are read apart`() =
        runTest {
            answer(RADARR_ONE, body = RADARR_ONE_PROFILES)
            answer("/api/v1/service/sonarr/1", body = """{"profiles":[{"id":7,"name":"WEB-1080p"}]}""")

            val lookup = labels().forDestinations(listOf(destination(7), destination(7, isTv = true)))

            assertEquals(listOf("HD-720p", "WEB-1080p"), listOf(lookup.of(7), lookup.of(7, isTv = true)))
        }

    @Test
    fun `a read is trusted for hours, not for the life of the process`() =
        runTest {
            answer(RADARR_ONE, body = RADARR_ONE_PROFILES)
            val labels = labels()
            labels.forDestinations(listOf(destination(7)))

            now = 2 * HOUR_MILLIS
            labels.forDestinations(listOf(destination(7)))
            assertEquals(1, profileReads())

            answer(RADARR_ONE, body = """{"profiles":[{"id":7,"name":"Renamed"}]}""")
            now = 4 * HOUR_MILLIS
            assertEquals("Renamed", labels.forDestinations(listOf(destination(7))).of(7))
            assertEquals(2, profileReads())
        }

    @Test
    fun `a failed read is remembered briefly, so a list of rows does not retry per row`() =
        runTest {
            answer(RADARR_ONE, code = 403)
            val labels = labels()

            labels.forDestinations(listOf(destination(7)))
            now = MINUTE_MILLIS
            labels.forDestinations(listOf(destination(7)))
            assertEquals(1, profileReads())

            answer(RADARR_ONE, body = RADARR_ONE_PROFILES)
            now = 10 * MINUTE_MILLIS
            assertEquals("HD-720p", labels.forDestinations(listOf(destination(7))).of(7))
            assertEquals(2, profileReads())
        }
}
