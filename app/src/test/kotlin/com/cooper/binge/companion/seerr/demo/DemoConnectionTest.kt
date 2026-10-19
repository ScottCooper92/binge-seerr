package com.cooper.binge.companion.seerr.demo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.CarriedCredentials
import com.cooper.binge.companion.seerr.auth.ConnectionCarrier
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun TestScope.connection() =
        SeerrConnection(
            store =
                CredentialStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                    PlainCipher,
                ),
            apis = SeerrApiFactory(logRequests = false),
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

            val failure = runCatchingFailure { connection.api().regions() }

            assertEquals(SeerrError.Server, failure.toSeerrError())
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
