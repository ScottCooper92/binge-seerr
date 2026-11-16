package com.cooper.binge.companion.seerr.ui.search

import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import com.cooper.binge.companion.seerr.ui.LicencesRoute
import com.cooper.binge.companion.seerr.ui.NotificationAgentRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.ThisAppRoute
import com.cooper.binge.companion.seerr.ui.UserSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the settings search offers, per viewer and per server: the gate of every entry is the gate of its page. */
class SettingsIndexTest {
    private val latest = SeerrServerProfile(variant = SeerrVariant.Seerr, version = null)
    private val overseerr = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = SeerrVersion(1, 33, 2))
    private val admin = SearchAccess(userId = 1, isAdmin = true, profile = latest)
    private val member = SearchAccess(userId = 7, isAdmin = false, profile = latest)

    private fun offered(access: SearchAccess) = settingsIndex.filter { it.available(access) }

    private fun SearchAccess.opens(target: SearchTarget) = offered(this).any { it.target == target }

    @Test
    fun `every server settings page has an entry`() {
        val covered =
            settingsIndex
                .map { it.target }
                .filterIsInstance<SearchTarget.Server>()
                .map { it.page }
                .toSet()
        assertEquals(ServerSettingsPage.entries.toSet(), covered)
    }

    @Test
    fun `every page of the viewer's own settings has an entry, except the permissions only a manager of another user sees`() {
        val covered =
            settingsIndex
                .map { it.target }
                .filterIsInstance<SearchTarget.Account>()
                .map { it.page }
                .toSet()
        assertEquals(UserSettingsPage.entries.toSet() - UserSettingsPage.Permissions, covered)
    }

    @Test
    fun `every notification agent has an entry`() {
        val covered =
            settingsIndex
                .map { it.target }
                .filterIsInstance<SearchTarget.Agent>()
                .map { it.agent }
                .toSet()
        assertEquals(ServerAgent.entries.toSet(), covered)
    }

    @Test
    fun `no two entries open the same page under the same label`() {
        val keys = settingsIndex.map { it.target to it.label }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `every entry has an id of its own, and it is not a label or a resource number`() {
        val ids = settingsIndex.map { it.id }

        assertEquals("two entries share an id", ids.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[a-z.A-Z-]+")) })
        assertEquals("server.Logs", settingsIndex.single { it.target == SearchTarget.Server(ServerSettingsPage.Logs) }.id)
        assertEquals("this-app", settingsIndex.single { it.target == SearchTarget.ThisApp }.id)
    }

    @Test
    fun `an admin is offered the server's settings`() {
        assertTrue(admin.opens(SearchTarget.Server(ServerSettingsPage.Services)))
        assertTrue(admin.opens(SearchTarget.Server(ServerSettingsPage.Cache)))
        assertTrue(admin.opens(SearchTarget.Agent(ServerAgent.Discord)))
    }

    @Test
    fun `a viewer who is not an admin is offered their own pages and This app, and nothing of the server's`() {
        val targets = offered(member).map { it.target }
        assertTrue(targets.none { it is SearchTarget.Server || it is SearchTarget.Agent })
        assertTrue(member.opens(SearchTarget.ThisApp))
        assertTrue(member.opens(SearchTarget.Licences))
        assertTrue(member.opens(SearchTarget.Account(UserSettingsPage.General)))
    }

    @Test
    fun `the password page needs a local sign-in, which an admin has regardless`() {
        val noLocal = latest.copy(settings = SeerrPublicSettings(localLogin = false))
        val password = SearchTarget.Account(UserSettingsPage.Password)

        assertFalse(member.copy(profile = noLocal).opens(password))
        assertTrue(admin.copy(profile = noLocal).opens(password))
        assertTrue(member.opens(password))
    }

    @Test
    fun `a page the server's version lacks is not offered`() {
        val old = SearchAccess(1, isAdmin = true, profile = overseerr)

        assertFalse(old.opens(SearchTarget.Server(ServerSettingsPage.Network)))
        assertFalse(old.opens(SearchTarget.Server(ServerSettingsPage.Metadata)))
        assertFalse(old.opens(SearchTarget.Server(ServerSettingsPage.BlocklistTags)))
        assertFalse(old.opens(SearchTarget.Account(UserSettingsPage.LinkedAccounts)))
        assertTrue(admin.opens(SearchTarget.Server(ServerSettingsPage.Network)))
        assertTrue(admin.opens(SearchTarget.Account(UserSettingsPage.LinkedAccounts)))
    }

    @Test
    fun `Tautulli is offered on a Plex server only`() {
        val plex = SeerrPublicSettings(mediaServerType = 1)
        val jellyfin = SeerrPublicSettings(mediaServerType = 2)
        val tautulli = SearchTarget.Server(ServerSettingsPage.Tautulli)

        assertTrue(admin.copy(profile = latest.copy(settings = plex)).opens(tautulli))
        assertFalse(admin.copy(profile = latest.copy(settings = jellyfin)).opens(tautulli))
    }

    @Test
    fun `an entry opens the route its page is reached by`() {
        assertEquals(ServerSettingsPageRoute(ServerSettingsPage.Logs), SearchTarget.Server(ServerSettingsPage.Logs).route(7))
        assertEquals(NotificationAgentRoute(ServerAgent.Slack), SearchTarget.Agent(ServerAgent.Slack).route(7))
        assertEquals(UserSettingsPageRoute(7, UserSettingsPage.General), SearchTarget.Account(UserSettingsPage.General).route(7))
        assertEquals(ThisAppRoute, SearchTarget.ThisApp.route(7))
        assertEquals(LicencesRoute, SearchTarget.Licences.route(7))
    }
}
