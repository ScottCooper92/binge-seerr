package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrDvrTestBody
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

private const val SERVER_ID = 1

/**
 * The request flow's server list and the Settings > Services pages are two views of one set of servers, as on a real Seerr
 * (#1820): a change made on a settings page is what the next request reads.
 */
class DemoServicesTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    @Test
    fun `as it ships, each kind offers one default server named as in Settings`() =
        demo { api ->
            val radarr = api.radarrServers().single()
            val sonarr = api.sonarrServers().single()

            assertEquals(SERVER_ID, radarr.id)
            assertEquals("Demo Radarr", radarr.name)
            assertTrue(radarr.isDefault)
            assertEquals(SERVER_ID, sonarr.id)
            assertEquals("Demo Sonarr", sonarr.name)
            assertTrue(sonarr.isDefault)
        }

    @Test
    fun `un-defaulting a server in Settings is what the request flow reads next`() =
        demo { api ->
            api.updateDvr("radarr", SERVER_ID, SeerrServiceSettingsDto(isDefault = false, syncEnabled = true))

            assertEquals(false, api.radarrServers().single().isDefault)
            assertTrue("Sonarr is its own kind", api.sonarrServers().single().isDefault)
        }

    @Test
    fun `a server added in Settings is offered, and one removed is not`() =
        demo { api ->
            val added = api.createDvr("sonarr", SeerrServiceSettingsDto(name = "Second Sonarr", hostname = "sonarr2.invalid", port = 8989))

            assertEquals(listOf("Demo Sonarr", "Second Sonarr"), api.sonarrServers().map { it.name })
            assertEquals("Second Sonarr", api.sonarrServer(checkNotNull(added.id)).server?.name)

            api.deleteDvr("sonarr", checkNotNull(added.id))

            assertEquals(listOf("Demo Sonarr"), api.sonarrServers().map { it.name })
        }

    @Test
    fun `a server renamed in Settings shows its new name in the request flow`() =
        demo { api ->
            api.updateDvr("radarr", SERVER_ID, SeerrServiceSettingsDto(name = "Renamed Radarr", isDefault = true, syncEnabled = true))

            assertEquals("Renamed Radarr", api.radarrServers().single().name)
            assertEquals("Renamed Radarr", api.radarrServer(SERVER_ID).server?.name)
        }

    @Test
    fun `a server's details keep the demo's profiles and folder, and an id that is not a server is not found`() =
        demo { api ->
            val details = api.radarrServer(SERVER_ID)

            assertEquals(listOf("Any", "HD-1080p"), details.profiles.map { it.name })
            val missing =
                try {
                    api.radarrServer(NO_SUCH_SERVER)
                    null
                } catch (failure: HttpException) {
                    failure
                }
            assertEquals(404, missing?.code())
        }

    @Test
    fun `a server's update that carries the id is refused, as a validating Seerr refuses it`() =
        demo { api ->
            val refused =
                try {
                    api.updateDvr("radarr", SERVER_ID, SeerrServiceSettingsDto(id = SERVER_ID, name = "Same", isDefault = true))
                    null
                } catch (failure: HttpException) {
                    failure
                }
            assertEquals(400, refused?.code())
            assertEquals("Demo Radarr", api.radarrServers().single().name)
            api.updateDvr("radarr", SERVER_ID, SeerrServiceSettingsDto(name = "Renamed", isDefault = true))
            assertEquals("Renamed", api.radarrServers().single().name)
        }

    @Test
    fun `each kind's active directory is one of the folders its details offer, as on a real server`() =
        demo { api ->
            val radarr = api.radarrServers().single()
            val sonarr = api.sonarrServers().single()

            assertTrue(radarr.activeDirectory in api.radarrServer(radarr.id).rootFolders.map { it.path })
            assertTrue(sonarr.activeDirectory in api.sonarrServer(sonarr.id).rootFolders.map { it.path })
        }

    @Test
    fun `the settings page's connection test offers the folder each kind's server is set to`() =
        demo { api ->
            val body = SeerrDvrTestBody(hostname = "demo.invalid", port = 1, useSsl = false, apiKey = "demo")

            val radarr = api.radarrSettings().single().activeDirectory
            val sonarr = api.sonarrSettings().single().activeDirectory

            assertTrue(radarr in api.testDvr("radarr", body).rootFolders.map { it.path })
            assertTrue(sonarr in api.testDvr("sonarr", body).rootFolders.map { it.path })
        }
}

private const val NO_SUCH_SERVER = 99
