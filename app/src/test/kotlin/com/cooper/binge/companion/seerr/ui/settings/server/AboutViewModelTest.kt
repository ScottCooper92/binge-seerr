package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.contributeUrl
import com.cooper.binge.companion.seerr.seerr.discordUrl
import com.cooper.binge.companion.seerr.seerr.discussionsUrl
import com.cooper.binge.companion.seerr.seerr.docsUrl
import com.cooper.binge.companion.seerr.seerr.githubUrl
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.REQUEST
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)

    @Before
    fun setUp() {
        seerr.start()
    }

    @After
    fun tearDown() = seerr.close()

    private suspend fun TestScope.ready(): AboutInfo {
        val vm = AboutViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
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
            assertEquals("https://github.com/seerr-team/seerr/discussions", info.variant.discussionsUrl())
            assertEquals("https://discord.gg/seerr", info.variant.discordUrl())
            assertEquals("https://opencollective.com/seerr", info.variant.contributeUrl())
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
            assertEquals("https://github.com/sponsors/sct", info.variant.contributeUrl())
        }

    @Test
    fun `a pull swaps in the new page, and a failed one keeps the page and says so`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/about", """{"version":"3.4.0","totalRequests":42}""")
            val vm = AboutViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            vm.uiState.first { it is AboutUiState.Ready }
            seerr.serve("GET /api/v1/settings/about", """{"version":"3.4.0","totalRequests":43}""")

            vm.refresh()

            val fresh = vm.uiState.first { it is AboutUiState.Ready && it.info.totalRequests == 43 } as AboutUiState.Ready
            assertFalse(fresh.isRefreshing)

            seerr.serve("GET /api/v1/status", code = 503)
            seerr.serve("GET /api/v1/settings/public", code = 503)
            val failed = awaitEvent(vm.events)
            vm.refresh()

            assertEquals(AboutEvent.RefreshFailed(SeerrError.Server), failed.await())
            val kept = vm.uiState.first { it is AboutUiState.Ready && !it.isRefreshing } as AboutUiState.Ready
            assertEquals(43, kept.info.totalRequests)
        }

    @Test
    fun `a pull where only the totals fail keeps the earlier totals, marked out of date, and the rest reads fresh`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve(
                "GET /api/v1/settings/about",
                """{"version":"3.4.0","totalRequests":42,"totalMediaItems":900,"tz":"Europe/London"}""",
            )
            seerr.serve("GET /api/v1/status/appdata", """{"appData":true,"appDataPath":"/app/config","appDataPermissions":true}""")
            val vm = AboutViewModel(seerr.connection(this), mainDispatcherRule.dispatcher)
            vm.uiState.first { it is AboutUiState.Ready }
            seerr.serve("GET /api/v1/settings/about", code = 503)
            seerr.serve("GET /api/v1/status/appdata", """{"appData":false,"appDataPath":"/app/config","appDataPermissions":true}""")

            vm.refresh()

            val info =
                (
                    vm.uiState.first {
                        it is AboutUiState.Ready && !it.isRefreshing && !it.info.appDataMounted!!
                    } as AboutUiState.Ready
                ).info
            assertEquals(AboutRead.Stale, info.aboutRead)
            assertEquals(42, info.totalRequests)
            assertEquals(900, info.totalMediaItems)
            assertEquals("Europe/London", info.timezone)
            assertEquals(AboutRead.Read, info.appDataRead)
            assertTrue(info.appDataWarning)
        }

    @Test
    fun `a first read where the totals fail has nothing to keep and says it could not read them`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/about", code = 503)
            val info = ready()

            assertEquals(AboutRead.Failed, info.aboutRead)
            assertNull(info.totalRequests)
            assertNull(info.timezone)
        }

    @Test
    fun `a server that refuses the totals shows no marker, since there is nothing to retry`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/about", code = 403)
            seerr.serve("GET /api/v1/status/appdata", code = 404)
            val info = ready()

            assertEquals(AboutRead.Unavailable, info.aboutRead)
            assertEquals(AboutRead.Unavailable, info.appDataRead)
        }
}
