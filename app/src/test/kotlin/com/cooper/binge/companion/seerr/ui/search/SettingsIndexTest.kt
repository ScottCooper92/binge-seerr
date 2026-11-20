package com.cooper.binge.companion.seerr.ui.search

import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import com.cooper.binge.companion.seerr.ui.DiagnosticsRoute
import com.cooper.binge.companion.seerr.ui.LicencesRoute
import com.cooper.binge.companion.seerr.ui.NotificationAgentRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.ThisAppRoute
import com.cooper.binge.companion.seerr.ui.UserSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.settings.server.AgentOption
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.settings.server.offeredAgents
import com.cooper.binge.companion.seerr.ui.settings.server.rowKey
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/** What the settings search offers, per viewer and per server: the gate of every entry is the gate of its page. */
class SettingsIndexTest {
    private val latest = SeerrServerProfile(variant = SeerrVariant.Seerr, version = null)
    private val overseerr = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = SeerrVersion(1, 33, 2))
    private val admin = SearchAccess(userId = 1, isAdmin = true, profile = latest)
    private val member = SearchAccess(userId = 7, isAdmin = false, profile = latest)

    private fun offered(access: SearchAccess) = settingsIndex.filter { it.available(access) }

    private fun SearchAccess.opens(target: SearchTarget) = offered(this).any { it.target == target && it.row == null }

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
    fun `no two entries open the same page, label and row`() {
        val keys = settingsIndex.map { Triple(it.target, it.label, it.row) }
        assertEquals(keys.size, keys.toSet().size)
    }

    private val rowConstants: Map<String, String> =
        SettingRows::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { it.name to it.get(null) as String }

    @Test
    fun `every row an entry names is a key SettingRows or an agent option declares, and every key is named`() {
        val named = settingsIndex.mapNotNull { it.row }.toSet()
        // An agent option's key is built from the option, which names it, so it has no constant of its own.
        val declared = rowConstants.values.toSet() + AgentOption.entries.filter { it.ownControl }.map { it.rowKey() }
        assertTrue(rowConstants.isNotEmpty())
        assertEquals("a key no entry names", emptySet<String>(), declared - named)
        assertEquals("a row no key declares", emptySet<String>(), named - declared)
    }

    /**
     * The key an entry names has to be carried by a row, or the search opens a page and finds nothing to wash. Each page's rows are built
     * in its screen file, so each key must be given to a row there (or picked for it by a `->` arm there): a row the page drops, or a
     * key the screen never uses, fails here.
     */
    @Test
    fun `every key an entry names is given to a row in its page's screen`() {
        val screens =
            mapOf(
                "general." to "settings/server/ServerGeneralScreen.kt",
                "network." to "settings/server/NetworkScreen.kt",
                "users." to "settings/server/ServerUsersScreen.kt",
                "metadata." to "settings/server/MetadataScreen.kt",
                "account." to "users/settings/GeneralSettingsScreen.kt",
                "notifications." to "users/settings/NotificationsSettingsScreen.kt",
                "app." to "settings/AppSettingsRows.kt",
            )
        val root = File("src/main/kotlin/com/cooper/binge/companion/seerr/ui")
        rowConstants.forEach { (name, key) ->
            val file = screens.entries.single { key.startsWith(it.key) }.value
            val given = Regex("(key = |-> )SettingRows\\.$name\\b").containsMatchIn(File(root, file).readText())
            assertTrue("$key is not given to a row in $file", given)
        }
    }

    @Test
    fun `an entry on a row opens its page with that row, and a page entry opens at the top`() {
        val url = settingsIndex.single { it.row == SettingRows.GENERAL_URL }
        assertEquals(ServerSettingsPageRoute(ServerSettingsPage.General, SettingRows.GENERAL_URL), url.target.route(7, url.row))
        assertEquals(ServerSettingsPageRoute(ServerSettingsPage.Cache), SearchTarget.Server(ServerSettingsPage.Cache).route(7))
        val name = settingsIndex.single { it.row == SettingRows.ACCOUNT_DISPLAY_NAME }
        assertEquals(UserSettingsPageRoute(7, UserSettingsPage.General, SettingRows.ACCOUNT_DISPLAY_NAME), name.target.route(7, name.row))
    }

    private fun generalRowShown(
        access: SearchAccess,
        row: String,
    ) = settingsIndex.single { it.target == SearchTarget.Server(ServerSettingsPage.General) && it.row == row }.available(access)

    @Test
    fun `a setting a page hides on some servers has the same gate as the page does`() {
        val old = SearchAccess(1, isAdmin = true, profile = overseerr)

        assertTrue(generalRowShown(admin, SettingRows.GENERAL_STREAMING_REGION))
        assertFalse(generalRowShown(old, SettingRows.GENERAL_STREAMING_REGION))
        assertFalse(generalRowShown(old, SettingRows.GENERAL_SPECIAL_EPISODES))
        assertTrue(generalRowShown(old, SettingRows.GENERAL_TRUST_PROXY))
        assertFalse(generalRowShown(admin, SettingRows.GENERAL_TRUST_PROXY))
    }

    @Test
    fun `a setting on a page the server lacks is not offered either`() {
        val old = SearchAccess(1, isAdmin = true, profile = overseerr)

        assertTrue(offered(admin).any { it.row == SettingRows.NETWORK_CSRF })
        assertTrue(offered(admin).any { it.row == SettingRows.METADATA_TV })
        assertTrue(offered(old).none { it.row == SettingRows.NETWORK_CSRF })
        assertTrue(offered(old).none { it.row == SettingRows.METADATA_TV })
    }

    @Test
    fun `a viewer who is not an admin is offered the settings on their own page, not the server's`() {
        val rows = offered(member).mapNotNull { it.row }
        assertTrue(SettingRows.ACCOUNT_EMAIL in rows)
        assertTrue(rows.none { it.startsWith("general.") || it.startsWith("network.") })
    }

    @Test
    fun `every entry has an id of its own, a setting's with its row, and it is not a label or a resource number`() {
        val ids = settingsIndex.map { it.id }

        assertEquals("two entries share an id", ids.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[a-zA-Z.|-]+")) })
        assertEquals("server.Logs|", settingsIndex.single { it.target == SearchTarget.Server(ServerSettingsPage.Logs) }.id)
        assertEquals("this-app|", settingsIndex.single { it.target == SearchTarget.ThisApp && it.row == null }.id)
        assertEquals("this-app|app.shake-to-report", settingsIndex.single { it.row == SettingRows.APP_SHAKE_TO_REPORT }.id)
        assertEquals("server.General|general.url", settingsIndex.single { it.row == SettingRows.GENERAL_URL }.id)
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
        assertTrue(member.opens(SearchTarget.Diagnostics))
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
        assertEquals(ThisAppRoute(), SearchTarget.ThisApp.route(7))
        assertEquals(ThisAppRoute(SettingRows.APP_SHAKE_TO_REPORT), SearchTarget.ThisApp.route(7, SettingRows.APP_SHAKE_TO_REPORT))
        assertEquals(LicencesRoute, SearchTarget.Licences.route(7))
        assertEquals(DiagnosticsRoute, SearchTarget.Diagnostics.route(7))
    }

    @Test
    fun `each device switch has its own This app entry on its row`() {
        val appEntries = settingsIndex.filter { it.target == SearchTarget.ThisApp && it.row != null }

        assertEquals(deviceSwitches.keys, appEntries.map { it.row }.toSet() - SettingRows.APP_REPORT_BUG)
        assertTrue(appEntries.all { it.available(admin) })
    }

    @Test
    fun `Report a bug is a This app entry on its own row, which is a link and not a switch`() {
        val entry = settingsIndex.single { it.row == SettingRows.APP_REPORT_BUG }

        assertEquals(SearchTarget.ThisApp, entry.target)
        assertEquals("this-app|app.report-bug", entry.id)
        assertTrue(SettingRows.APP_REPORT_BUG !in deviceSwitches.keys)
        assertEquals(ThisAppRoute(SettingRows.APP_REPORT_BUG), entry.target.route(7, entry.row))
    }

    @Test
    fun `new sign-ups is an entry on the server's Users page under a fixed label, not the server's name`() {
        val entry = settingsIndex.single { it.row == SettingRows.USERS_NEW_SIGN_UPS }

        assertEquals(SearchTarget.Server(ServerSettingsPage.Users), entry.target)
        assertEquals(R.string.server_settings_new_sign_ups, entry.label)
        assertEquals("server.Users|users.new-sign-ups", entry.id)
        assertTrue(entry.available(admin))
        assertTrue(!entry.available(member))
    }

    @Test
    fun `the Telegram thread row is offered only where the server keeps a topic`() {
        val thread = settingsIndex.single { it.row == SettingRows.NOTIFICATIONS_TELEGRAM_THREAD_ID }
        val withTopics = SearchAccess(userId = 7, isAdmin = false, profile = latest)

        assertEquals(UserSettingsPage.Notifications, (thread.target as SearchTarget.Account).page)
        assertTrue(thread.available(withTopics))
        assertTrue(!thread.available(SearchAccess(userId = 7, isAdmin = false, profile = overseerr)))
    }

    @Test
    fun `every notification field on the page has an entry on its row for any signed-in viewer`() {
        val rows = settingsIndex.filter { it.target == SearchTarget.Account(UserSettingsPage.Notifications) }.mapNotNull { it.row }
        assertTrue(rows.size >= 9)
        assertTrue(rows.all { it.startsWith("notifications.") })
        assertTrue(SettingRows.NOTIFICATIONS_DISCORD_ID in offered(member).mapNotNull { it.row })
    }

    @Test
    fun `each notification field entry names its agent in its path and its keywords`() {
        val agentPage =
            mapOf(
                SettingRows.NOTIFICATIONS_PGP_KEY to R.string.search_page_notifications_email,
                SettingRows.NOTIFICATIONS_DISCORD_ID to R.string.search_page_notifications_discord,
                SettingRows.NOTIFICATIONS_PUSHBULLET_TOKEN to R.string.search_page_notifications_pushbullet,
                SettingRows.NOTIFICATIONS_PUSHOVER_APP_TOKEN to R.string.search_page_notifications_pushover,
                SettingRows.NOTIFICATIONS_PUSHOVER_USER_KEY to R.string.search_page_notifications_pushover,
                SettingRows.NOTIFICATIONS_PUSHOVER_SOUND to R.string.search_page_notifications_pushover,
                SettingRows.NOTIFICATIONS_TELEGRAM_CHAT_ID to R.string.search_page_notifications_telegram,
                SettingRows.NOTIFICATIONS_TELEGRAM_THREAD_ID to R.string.search_page_notifications_telegram,
                SettingRows.NOTIFICATIONS_TELEGRAM_SILENT to R.string.search_page_notifications_telegram,
            )
        val fields = settingsIndex.filter { it.target == SearchTarget.Account(UserSettingsPage.Notifications) && it.row != null }

        assertEquals(agentPage.keys, fields.mapNotNull { it.row }.toSet())
        fields.forEach { entry ->
            assertEquals(agentPage.getValue(entry.row!!), entry.pageLabel)
            assertEquals(agentPage.getValue(entry.row!!), entry.keywords)
        }
    }

    @Test
    fun `every option an agent's page draws as a row has an entry on that agent, opening the page on it`() {
        val options = AgentOption.entries.filter { it.ownControl }
        val entries = settingsIndex.filter { it.target is SearchTarget.Agent && it.row != null }

        assertEquals(options.map { it.rowKey() }.toSet(), entries.mapNotNull { it.row }.toSet())
        assertEquals(options.size, entries.size)
        entries.forEach { entry ->
            val agent = (entry.target as SearchTarget.Agent).agent
            assertEquals(NotificationAgentRoute(agent, entry.row), entry.target.route(1, entry.row))
            assertTrue(entry.row!!.startsWith("agent.${agent.segment}."))
        }
    }

    @Test
    fun `an agent's option is offered only where the server has the agent`() {
        fun offeredRows(access: SearchAccess) = offered(access).mapNotNull { it.row }.toSet()

        assertTrue(AgentOption.NtfyTopic.rowKey() in offeredRows(admin))
        assertTrue(AgentOption.NtfyTopic.rowKey() !in offeredRows(SearchAccess(1, true, overseerr)))
        assertTrue(AgentOption.LunaSeaProfileName.rowKey() in offeredRows(SearchAccess(1, true, overseerr)))
        assertTrue(AgentOption.LunaSeaProfileName.rowKey() !in offeredRows(admin))
        assertTrue(AgentOption.TelegramMessageThreadId.rowKey() in offeredRows(admin))
        assertTrue(AgentOption.TelegramMessageThreadId.rowKey() !in offeredRows(SearchAccess(1, true, overseerr)))
        assertTrue(AgentOption.DiscordWebhookUrl.rowKey() !in offeredRows(member))
    }

    @Test
    fun `the agent page gives each option's row its key`() {
        val screen = File("src/main/kotlin/com/cooper/binge/companion/seerr/ui/settings/server/NotificationAgentScreen.kt").readText()

        assertTrue(screen.contains(".copy(key = option.rowKey())"))
    }

    @Test
    fun `an agent's page is offered only where the server has the agent`() {
        fun agentsOffered(access: SearchAccess) =
            offered(access)
                .map { it.target }
                .filterIsInstance<SearchTarget.Agent>()
                .map { it.agent }
                .toSet()

        val onOverseerr = agentsOffered(SearchAccess(1, true, overseerr))
        val onSeerr = agentsOffered(admin)

        assertTrue(ServerAgent.Ntfy !in onOverseerr)
        assertTrue(ServerAgent.LunaSea in onOverseerr)
        assertTrue(ServerAgent.LunaSea !in onSeerr)
        assertTrue(ServerAgent.Ntfy in onSeerr)
        assertTrue(ServerAgent.Discord in onOverseerr && ServerAgent.Discord in onSeerr)
        assertEquals(admin.profile.offeredAgents().toSet(), onSeerr)
        assertEquals(overseerr.offeredAgents().toSet(), onOverseerr)
    }

    @Test
    fun `the movie and TV request limits are entries on the server's Users page, on their own rows`() {
        val limits = settingsIndex.filter { it.row == SettingRows.USERS_MOVIE_LIMIT || it.row == SettingRows.USERS_TV_LIMIT }

        assertEquals(2, limits.size)
        assertTrue(limits.all { it.target == SearchTarget.Server(ServerSettingsPage.Users) })
        assertEquals(setOf(R.string.server_settings_movie_limit, R.string.server_settings_tv_limit), limits.map { it.label }.toSet())
        assertTrue(limits.all { it.available(admin) && !it.available(member) })
    }
}
