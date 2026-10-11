package com.cooper.binge.companion.seerr.handoff

import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.InMemoryDataStore
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
    fun `connecting again leaves none remembered`() =
        runTest {
            val server = seerr.server.url("/").toString()
            val connection = seerr.connection(this, onServerChanged = { memory.clear() })
            memory.remember(server, "http://192.168.1.20:5055/")

            connection.connect(server, SeerrAuth.ApiKey("another")).getOrThrow()

            assertEquals(emptyList<String>(), memory.remembered(server))
        }
}
