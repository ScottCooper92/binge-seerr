package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrBulkUsersBody
import com.cooper.binge.companion.seerr.seerr.SeerrCreateUserBody
import com.cooper.binge.companion.seerr.seerr.SeerrDiscoverSliderBody
import com.cooper.binge.companion.seerr.seerr.SeerrImportJellyfinBody
import com.cooper.binge.companion.seerr.seerr.SeerrJobScheduleBody
import com.cooper.binge.companion.seerr.seerr.SeerrLibraryEnabledBody
import com.cooper.binge.companion.seerr.seerr.SeerrMainSettingsUpdateBody
import com.cooper.binge.companion.seerr.seerr.SeerrNotificationAgentDto
import com.cooper.binge.companion.seerr.seerr.SeerrOverrideRuleDto
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserMainSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserPermissionsBody
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/** The users and the settings pages through the real client: a change saved on a page is what the next read of it shows. */
class DemoSettingsTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    @Test
    fun `the users list pages, sorts and counts each user's requests`() =
        demo { api ->
            val page = api.users(take = 10, sort = "requests")

            assertEquals(3, page.pageInfo.results)
            assertEquals(setOf("Alex", "Sam", "Jordan"), page.results.map { it.displayName }.toSet())
            assertTrue(page.results.first().requestCount!! >= page.results.last().requestCount!!)
        }

    @Test
    fun `a user can be added, given permissions, changed in bulk and removed`() =
        demo { api ->
            val added = api.createUser(SeerrCreateUserBody(email = "casey@example.com", username = "Casey", password = null))

            api.updateUserPermissions(added.id, SeerrUserPermissionsBody(permissions = 2))
            assertEquals(2, api.userPermissions(added.id).permissions)
            api.bulkUpdateUsers(SeerrBulkUsersBody(ids = listOf(added.id, 2), permissions = 8))
            assertEquals(8, api.user(2).permissions)
            api.deleteUser(added.id)

            assertEquals(3, api.userCountProbe().pageInfo.results)
        }

    @Test
    fun `the signed-in account cannot be deleted, and an imported account joins the list`() =
        demo { api ->
            val refused = runCatching { api.deleteUser(DEMO_USER_ID) }.exceptionOrNull() as HttpException
            assertEquals(403, refused.code())

            val offered = api.jellyfinUsers().map { it.id }
            val imported = api.importFromJellyfin(SeerrImportJellyfinBody(offered.take(1)))

            assertEquals(1, imported.size)
            assertEquals(4, api.userCountProbe().pageInfo.results)
        }

    @Test
    fun `a user's own settings keep what was saved`() =
        demo { api ->
            api.updateUserMainSettings(2, SeerrUserMainSettingsDto(username = "Samuel", movieQuotaLimit = 5))

            assertEquals("Samuel", api.userMainSettings(2).username)
            assertEquals(5, api.userMainSettings(2).movieQuotaLimit)
            assertEquals("Samuel", api.user(2).displayName)
        }

    @Test
    fun `the general settings keep a change and a new API key replaces the old one`() =
        demo { api ->
            val before = api.mainSettings().apiKey

            api.updateMainSettings(SeerrMainSettingsUpdateBody(applicationTitle = "Front room", hideAvailable = true))
            val renewed = api.regenerateApiKey().apiKey

            assertEquals("Front room", api.mainSettings().applicationTitle)
            assertEquals(true, api.mainSettings().hideAvailable)
            assertNotEquals(before, renewed)
            assertEquals(renewed, api.mainSettings().apiKey)
        }

    @Test
    fun `a library switches off, and a job takes a new schedule`() =
        demo { api ->
            api.setLibraryEnabled("jellyfin", "shows", SeerrLibraryEnabledBody(enabled = false))
            val job = api.jobs().first()
            api.scheduleJob(job.id, SeerrJobScheduleBody("0 * * * * *"))

            assertFalse(api.mediaLibraries("jellyfin").first { it.id == "shows" }.enabled)
            assertEquals("custom", api.jobs().first { it.id == job.id }.interval)
        }

    @Test
    fun `a download server, a rule and a slider can be added, edited and removed`() =
        demo { api ->
            val server = api.createDvr("radarr", SeerrServiceSettingsDto(name = "Second Radarr", hostname = "radarr2.invalid", port = 7878))
            api.updateDvr("radarr", server.id!!, SeerrServiceSettingsDto(name = "Renamed"))
            assertEquals(listOf("Demo Radarr", "Renamed"), api.radarrSettings().map { it.name })
            api.deleteDvr("radarr", server.id!!)
            assertEquals(1, api.radarrSettings().size)

            val rule = api.createOverrideRule(SeerrOverrideRuleDto(genre = "35"))
            api.deleteOverrideRule(rule.id!!)
            assertEquals(1, api.overrideRules().size)

            val slider = api.addDiscoverSlider(SeerrDiscoverSliderBody(title = "Open films", type = 10, data = "1"))
            assertEquals(6, api.discoverSliders().size)
            api.deleteDiscoverSlider(slider.id!!)
            api.resetDiscoverSliders()
            assertEquals(5, api.discoverSliders().size)
        }

    @Test
    fun `a notification agent keeps what was saved, and the log filters by level`() =
        demo { api ->
            api.updateNotificationAgent("discord", SeerrNotificationAgentDto(enabled = true, types = 4))

            assertTrue(api.notificationAgent("discord").enabled)
            assertEquals(false, api.notificationAgent("slack").enabled)
            assertTrue(api.logs(take = 20, skip = 0, filter = "warn").toString().contains("No notification agent"))
            assertFalse(api.logs(take = 20, skip = 0, filter = "warn").toString().contains("Demo server started"))
        }

    @Test
    fun `the pickers have something to pick`() =
        demo { api ->
            assertTrue(api.regions().isNotEmpty())
            assertTrue(api.genres("movie", "en").isNotEmpty())
            assertEquals(1, api.searchKeywords("space").results.size)
            assertEquals("Blender Foundation", api.studio(1).name)
        }
}
