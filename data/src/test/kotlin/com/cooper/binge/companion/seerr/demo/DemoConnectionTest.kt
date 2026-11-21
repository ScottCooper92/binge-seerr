package com.cooper.binge.companion.seerr.demo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.CarriedCredentials
import com.cooper.binge.companion.seerr.auth.ConnectionCarrier
import com.cooper.binge.companion.seerr.auth.ConnectionRestore
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrHealth
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.homelab.core.server.connection.ConnectionHealth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The demo server end to end through the real client: no socket exists, so a pass proves nothing left the process. */
class DemoConnectionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val carried = mutableListOf<CarriedCredentials>()
    private var cleared = 0

    private fun TestScope.connection(apis: SeerrApiFactory = SeerrApiFactory(logRequests = false)) =
        SeerrConnection(
            store =
                LegacyCredentialStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                    PlainCipher,
                    UnconfinedTestDispatcher(testScheduler),
                ),
            apis = apis,
            carrier =
                object : ConnectionCarrier {
                    override suspend fun put(carried: CarriedCredentials) {
                        this@DemoConnectionTest.carried += carried
                    }

                    override suspend fun read(): CarriedCredentials? = null

                    override suspend fun clear() {
                        cleared++
                    }
                },
        )

    @Test
    fun `the demo connects as an administrator on a current Seerr`() =
        runTest {
            val connection = connection()
            val demo = demoCredentials()

            connection.connect(demo.baseUrl, demo.auth).getOrThrow()

            assertTrue(connection.current().isDemo)
            assertEquals(SeerrVariant.Seerr, connection.profile().variant)
            assertEquals("blocklist", connection.profile().blocklistPath)
            assertEquals(2, connection.authenticatedUser().permissions)
        }

    @Test
    fun `viewing the demo as a requester re-reads the user and the profile`() =
        runTest {
            val connection = connection()
            val demo = demoCredentials()
            connection.connect(demo.baseUrl, demo.auth).getOrThrow()
            assertEquals(2, connection.authenticatedUser().permissions)
            assertFalse(connection.demoViewer)

            connection.viewDemoAs(true)

            assertEquals(32, connection.authenticatedUser().permissions)
            assertTrue(connection.demoViewer)
            assertEquals(SeerrVariant.Seerr, connection.profile().variant)

            connection.viewDemoAs(false)

            assertEquals(2, connection.authenticatedUser().permissions)
        }

    @Test
    fun `the demo is never carried off the device`() =
        runTest {
            val demo = demoCredentials()

            connection().connect(demo.baseUrl, demo.auth).getOrThrow()

            assertTrue(carried.isEmpty())
        }

    @Test
    fun `an endpoint the demo does not serve answers a server error the screens already show`() =
        runTest {
            val connection = connection()
            val demo = demoCredentials()
            connection.connect(demo.baseUrl, demo.auth).getOrThrow()

            val failure = runCatchingFailure { connection.api().plexServers() }

            assertEquals(SeerrError.Server, failure.toSeerrError())
        }

    @Test
    fun `endpoints the demo does not serve do not mark it unreachable`() =
        runTest {
            val monitor = SeerrHealth.standalone()
            val connection = connection(SeerrApiFactory(logRequests = false, health = monitor))
            val demo = demoCredentials()
            connection.connect(demo.baseUrl, demo.auth).getOrThrow()

            repeat(3) { runCatchingFailure { connection.api().plexServers() } }

            assertEquals(ConnectionHealth.Healthy, monitor.health.first())
        }

    @Test
    fun `starting the app with the demo saved does not carry it either`() =
        runTest {
            val store =
                LegacyCredentialStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                    PlainCipher,
                    UnconfinedTestDispatcher(testScheduler),
                )
            store.save(demoCredentials())
            val carrier =
                object : ConnectionCarrier {
                    override suspend fun put(carried: CarriedCredentials) {
                        this@DemoConnectionTest.carried += carried
                    }

                    override suspend fun read(): CarriedCredentials? = null

                    override suspend fun clear() = Unit
                }

            ConnectionRestore(store, SeerrApiFactory(logRequests = false), carrier, backgroundScope).refreshCarrier()

            assertTrue(carried.isEmpty())
        }

    @Test
    fun `disconnecting forgets the demo`() =
        runTest {
            val connection = connection()
            val demo = demoCredentials()
            connection.connect(demo.baseUrl, demo.auth).getOrThrow()

            connection.disconnect()

            assertNull(connection.credentials.first())
            assertEquals(1, cleared)
        }

    private suspend fun runCatchingFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (failure: Exception) {
            return failure
        }
        fail("expected the call to fail")
        error("unreachable")
    }
}
