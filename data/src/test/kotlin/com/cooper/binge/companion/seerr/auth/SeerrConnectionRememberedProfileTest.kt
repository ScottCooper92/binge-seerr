package com.cooper.binge.companion.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.util.ReversingCipher
import com.cooper.binge.companion.seerr.util.enqueueProfile
import com.cooper.binge.companion.seerr.util.routeProfiles
import com.cooper.homelab.core.foundation.ServerVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A cold launch: a second connection over the files the first one wrote. What it is pinned to is that the server's profile from
 * the last launch is there to draw a first frame from, that it is never taken for the server's answer, and that it goes with the
 * connection (#2453).
 */
class SeerrConnectionRememberedProfileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().routeProfiles().apply { start() }
    private val baseUrl = server.url("/").toString()

    /** One launch of the app: its own DataStore over the shared file, ended with [end]. */
    private inner class Launch(
        testScope: TestScope,
    ) {
        private val job = Job()
        val connection =
            SeerrConnection(
                store =
                    LegacyCredentialStore(
                        dataStore =
                            PreferenceDataStoreFactory.create(scope = CoroutineScope(testScope.coroutineContext + job)) {
                                folder.root.resolve("creds.preferences_pb")
                            },
                        cipher = ReversingCipher,
                        io = UnconfinedTestDispatcher(testScope.testScheduler),
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )

        suspend fun end() = job.cancelAndJoin()
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private fun serverIs(version: String) = server.enqueueProfile(json("""{"version":"$version"}"""), json("""{"initialized":true}"""))

    private suspend fun firstLaunch(testScope: TestScope): Launch {
        server.enqueue(json("""{"id":1,"permissions":2}"""))
        serverIs("2.7.0")
        return Launch(testScope).also { it.connection.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow() }
    }

    @Test
    fun `a cold launch knows the server's profile from the last one before it asks the server`() =
        runTest {
            val first = firstLaunch(this)
            first.end()
            val requests = server.requestCount

            val second = Launch(this)
            second.connection.credentials.first()

            assertEquals(ServerVersion.of(2, 7, 0), second.connection.cachedProfileOrNull()?.version)
            assertEquals("no request was made to know it", requests, server.requestCount)
            second.end()
        }

    @Test
    fun `before the connection is read there is nothing to know`() =
        runTest {
            firstLaunch(this).end()

            val second = Launch(this)

            assertNull(second.connection.cachedProfileOrNull())
            second.end()
        }

    @Test
    fun `the profile from the last launch is never the server's answer, and a fresh read replaces it`() =
        runTest {
            firstLaunch(this).end()
            val second = Launch(this)
            second.connection.credentials.first()
            serverIs("3.0.0")

            val read = second.connection.profile()

            assertEquals(ServerVersion.of(3, 0, 0), read.version)
            assertEquals(ServerVersion.of(3, 0, 0), second.connection.cachedProfileOrNull()?.version)
            second.end()
            val third = Launch(this)
            third.connection.credentials.first()
            assertEquals(ServerVersion.of(3, 0, 0), third.connection.cachedProfileOrNull()?.version)
            third.end()
        }

    @Test
    fun `disconnecting forgets the profile with the connection`() =
        runTest {
            val first = firstLaunch(this)
            first.connection.disconnect()
            first.end()

            val second = Launch(this)
            second.connection.credentials.first()

            assertNull(second.connection.cachedProfileOrNull())
            second.end()
        }
}
