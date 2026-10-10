package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The addresses sent to a TV cleared the way the app wires it: from the connection's server-changed hook. */
class HandOffMemoryDisconnectTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val memory = DataStoreHandOffAddressMemory(InMemoryDataStore())

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 7, permissions = ADMIN)
    }

    @After
    fun tearDown() = seerr.close()

    @Test
    fun `disconnecting leaves no address remembered for the server that was forgotten`() =
        runTest {
            val server = seerr.server.url("/").toString()
            val connection = seerr.connection(this, onServerChanged = { memory.clear() })
            memory.remember(server, "http://192.168.1.20:5055/")

            connection.disconnect()

            assertEquals(emptyList<String>(), memory.remembered(server))
        }

    @Test
    fun `switching to another server leaves none of the last one's`() =
        runTest {
            val server = seerr.server.url("/").toString()
            val connection = seerr.connection(this, onServerChanged = { memory.clear() })
            memory.remember(server, "http://192.168.1.20:5055/")

            connection.connect(server, SeerrAuth.ApiKey("another")).getOrThrow()

            assertEquals(emptyList<String>(), memory.remembered(server))
        }
}
