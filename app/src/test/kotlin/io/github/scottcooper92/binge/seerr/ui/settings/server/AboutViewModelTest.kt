package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.discordUrl
import io.github.scottcooper92.binge.seerr.seerr.docsUrl
import io.github.scottcooper92.binge.seerr.seerr.githubUrl
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.REQUEST
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AboutViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
    }

    @After
    fun tearDown() = seerr.close()

    private suspend fun TestScope.ready(): AboutInfo {
        val vm = AboutViewModel(seerr.connection(this))
        return (vm.uiState.first { it is AboutUiState.Ready } as AboutUiState.Ready).info
    }

    @Test
    fun `a seerr's about carries the totals, the timezone, and warns on a data directory that is not a mount`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/status", """{"version":"3.4.0","commitTag":"abc1234","updateAvailable":true,"commitsBehind":0}""")
            seerr.serve(
                "GET /api/v1/settings/about",
                """{"version":"3.4.0","totalRequests":42,"totalMediaItems":900,"tz":"Europe/London","appDataPath":"/app/config"}""",
            )
            seerr.serve("GET /api/v1/status/appdata", """{"appData":false,"appDataPath":"/app/config","appDataPermissions":true}""")
            val info = ready()
            assertEquals(SeerrVariant.Seerr, info.variant)
            assertEquals("3.4.0", info.versionLabel)
            assertEquals("abc1234", info.commitTag)
            assertTrue(info.updateAvailable)
            assertEquals(42, info.totalRequests)
            assertEquals(900, info.totalMediaItems)
            assertEquals("Europe/London", info.timezone)
            assertEquals("/app/config", info.appDataPath)
            assertEquals(false, info.appDataMounted)
            assertTrue(info.appDataWarning)
            assertEquals("https://github.com/seerr-team/seerr", info.variant.githubUrl())
        }

    @Test
    fun `a user without the admin's reads still gets the edition, and the links follow the fork`() =
        runTest {
            seerr.viewer(id = 2, permissions = REQUEST, version = "1.33.2", settings = "{}")
            seerr.serve("GET /api/v1/settings/about", """{"message":"forbidden"}""", code = 403)
            seerr.serve("GET /api/v1/status/appdata", """{"message":"forbidden"}""", code = 403)
            val info = ready()
            assertEquals(SeerrVariant.Overseerr, info.variant)
            assertEquals("1.33.2", info.versionLabel)
            assertNull(info.totalRequests)
            assertNull(info.appDataPath)
            assertFalse(info.appDataWarning)
            assertEquals("https://docs.overseerr.dev", info.variant.docsUrl())
            assertEquals("https://discord.gg/overseerr", info.variant.discordUrl())
            assertEquals("https://github.com/sct/overseerr", info.variant.githubUrl())
        }
}
