package io.github.scottcooper92.binge.seerr.data

import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The request cache cleared the way the app wires it: from the connection's server-changed hook. */
class RequestCacheClearTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 7, permissions = ADMIN)
    }

    @After
    fun tearDown() = seerr.close()

    private fun row(id: Int) =
        RequestEntity(
            listKey = "all:added:all",
            id = id,
            tmdbId = 100,
            mediaType = "Movie",
            title = "Heat",
            posterUrl = null,
            year = null,
            requestedBy = null,
            requestedById = null,
            requestedAtMillis = null,
            status = 1,
            mediaStatus = null,
            downloadFraction = null,
            downloadEtaMinutes = null,
            downloading = false,
            seasonNumbers = "",
            is4k = false,
            orderIndex = id,
        )

    @Test
    fun `switching or forgetting the server leaves no request rows or cursors of the last one`() =
        runTest {
            val store = FakeRequestStore()
            store.refresh("all:added:all", listOf(row(1), row(2)), nextSkip = 20)
            val connection = seerr.connection(this, onServerChanged = { store.clearAll() })
            store.refresh("all:added:all", listOf(row(1), row(2)), nextSkip = 20)

            connection.connect(seerr.server.url("/").toString(), SeerrAuth.ApiKey("another")).getOrThrow()

            assertEquals(emptyList<RequestEntity>(), store.rows)
            assertNull(store.nextSkip("all:added:all"))

            store.refresh("all:added:all", listOf(row(3)), nextSkip = null)
            connection.disconnect()

            assertEquals(emptyList<RequestEntity>(), store.rows)
        }
}
