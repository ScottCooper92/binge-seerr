package com.cooper.binge.companion.seerr.ui.search

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.ui.LicencesRoute
import com.cooper.binge.companion.seerr.ui.NotificationAgentRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.ThisAppRoute
import com.cooper.binge.companion.seerr.ui.UserSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.settings.server.icon
import com.cooper.binge.companion.seerr.ui.settings.server.labelRes
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.titleRes

/**
 * What the search needs to know about the viewer to decide what to offer: who they are, whether the server lets them administer it,
 * and what the connected server has. These are the same facts the hub and the pages gate on, so an entry shows exactly where its
 * page would open.
 */
internal data class SearchAccess(
    val userId: Int,
    val isAdmin: Boolean,
    val profile: SeerrServerProfile,
)

/**
 * Where an entry opens. Each is one of the app's own pages, so the search adds no screen of its own to open and cannot reach one the
 * app would not.
 */
internal sealed interface SearchTarget {
    /** The route this target opens for the viewer [userId], on the row with key [row] where the page has it; null opens the page at the top. */
    fun route(
        userId: Int,
        row: String? = null,
    ): SeerrRoute

    /** One of the server's settings pages, which the admin settings router lets only an admin open. */
    data class Server(
        val page: ServerSettingsPage,
    ) : SearchTarget {
        override fun route(
            userId: Int,
            row: String?,
        ): SeerrRoute = ServerSettingsPageRoute(page, row)
    }

    /** One notification agent's page. */
    data class Agent(
        val agent: ServerAgent,
    ) : SearchTarget {
        override fun route(
            userId: Int,
            row: String?,
        ): SeerrRoute = NotificationAgentRoute(agent)
    }

    /** One of the signed-in user's own settings pages. */
    data class Account(
        val page: UserSettingsPage,
    ) : SearchTarget {
        override fun route(
            userId: Int,
            row: String?,
        ): SeerrRoute = UserSettingsPageRoute(userId, page, row)
    }

    /** The This app page. */
    data object ThisApp : SearchTarget {
        override fun route(
            userId: Int,
            row: String?,
        ): SeerrRoute = ThisAppRoute
    }

    /** The open-source licences, which This app opens. */
    data object Licences : SearchTarget {
        override fun route(
            userId: Int,
            row: String?,
        ): SeerrRoute = LicencesRoute
    }
}

/**
 * One thing the search can take the user to: a page, or one setting on it ([row]). [keywords] is one string of other words people look
 * for it by, translated with the rest. Opening an entry opens its page, scrolled to and washing the row where it names one.
 */
internal class SettingEntry(
    val target: SearchTarget,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    @param:StringRes val section: Int,
    @param:StringRes val keywords: Int? = null,
    /** The page a setting is on, for its path in the results; null for an entry that is the page itself. */
    @param:StringRes val pageLabel: Int? = null,
    /** The row to scroll to and wash on the page, by its [SettingRows] key; null opens the page at the top. */
    val row: String? = null,
    /** A gate of the entry's own, for a page the server has only on some versions or media servers; the target's own gate applies besides. */
    val shown: (SearchAccess) -> Boolean = { true },
)

/** Whether the search may offer this entry: its page is one this viewer can open, and its own gate passes. */
internal fun SettingEntry.available(access: SearchAccess): Boolean = target.opensFor(access) && shown(access)

/** The page-level gate: the server's own settings need an admin, the viewer's own pages need nothing more than signing in. */
private fun SearchTarget.opensFor(access: SearchAccess): Boolean =
    when (this) {
        is SearchTarget.Server -> access.isAdmin && page.shownFor(access)
        is SearchTarget.Agent -> access.isAdmin
        is SearchTarget.Account -> page.opensFor(access)
        SearchTarget.ThisApp, SearchTarget.Licences -> true
    }

/** What the connected server has: a page of a feature only some servers have is offered, with every setting on it, only where this passes. */
private fun ServerSettingsPage.shownFor(access: SearchAccess): Boolean =
    when (this) {
        ServerSettingsPage.BlocklistTags -> access.profile.hasBlocklistFilters
        ServerSettingsPage.Tautulli -> access.profile.mediaServer == SeerrMediaServer.Plex
        ServerSettingsPage.DiscoverSliders -> access.profile.hasDiscoverSliders
        ServerSettingsPage.Network -> access.profile.hasNetworkSettings
        ServerSettingsPage.Metadata -> access.profile.hasMetadataSettings
        else -> true
    }

/** The viewer's own pages, by the rules [com.cooper.binge.companion.seerr.ui.users.settings.settingsPagesFor] applies to oneself. */
private fun UserSettingsPage.opensFor(access: SearchAccess): Boolean =
    when (this) {
        UserSettingsPage.General, UserSettingsPage.Notifications -> true
        UserSettingsPage.Password -> access.profile.settings.localLogin || access.isAdmin
        UserSettingsPage.LinkedAccounts -> access.profile.hasLinkedAccounts
        UserSettingsPage.Permissions -> false
    }

private fun server(
    page: ServerSettingsPage,
    @StringRes label: Int,
    icon: ImageVector,
    @StringRes keywords: Int? = null,
    shown: (SearchAccess) -> Boolean = { true },
) = SettingEntry(SearchTarget.Server(page), label, icon, R.string.hub_section_settings, keywords, shown = shown)

private fun account(
    page: UserSettingsPage,
    icon: ImageVector,
    @StringRes keywords: Int? = null,
) = SettingEntry(SearchTarget.Account(page), page.titleRes(), icon, R.string.search_section_account, keywords)

private fun agent(agent: ServerAgent) =
    SettingEntry(
        SearchTarget.Agent(agent),
        agent.labelRes(),
        agent.icon(),
        R.string.settings_section_notifications,
        R.string.search_kw_agent,
    )

/**
 * Everything the search knows: each page of the console the signed-in user can open, once, with where it is. A new page adds its
 * line, and a test holds that every page an enum lists has one. An entry shows only where [available] says its page does.
 */
internal val settingsIndex: List<SettingEntry> =
    buildList {
        add(account(UserSettingsPage.General, Icons.Filled.Tune, R.string.search_kw_account))
        add(account(UserSettingsPage.Password, Icons.Filled.Password, R.string.search_kw_password))
        add(account(UserSettingsPage.LinkedAccounts, Icons.Filled.Link, R.string.search_kw_linked))
        add(account(UserSettingsPage.Notifications, Icons.Filled.Notifications, R.string.search_kw_notifications))
        add(server(ServerSettingsPage.General, R.string.settings_group_general, Icons.Filled.Tune, R.string.search_kw_general))
        add(
            server(
                ServerSettingsPage.BlocklistTags,
                R.string.server_settings_blocklist_tags,
                Icons.Filled.Block,
                R.string.search_kw_blocklist_tags,
            ),
        )
        add(server(ServerSettingsPage.Users, R.string.hub_section_users, Icons.Filled.Group, R.string.search_kw_users))
        add(
            server(
                ServerSettingsPage.DefaultPermissions,
                R.string.server_settings_default_permissions,
                Icons.Filled.Security,
                R.string.search_kw_default_permissions,
            ),
        )
        add(
            server(
                ServerSettingsPage.MediaServer,
                R.string.server_settings_media_server,
                Icons.Filled.Storage,
                R.string.search_kw_media_server,
            ),
        )
        add(
            server(
                ServerSettingsPage.Tautulli,
                R.string.server_settings_tautulli,
                Icons.Filled.Insights,
                R.string.search_kw_tautulli,
            ),
        )
        add(server(ServerSettingsPage.Services, R.string.settings_section_services, Icons.Filled.Hub, R.string.search_kw_services))
        add(
            server(
                ServerSettingsPage.NotificationAgents,
                R.string.settings_section_notifications,
                Icons.Filled.Notifications,
                R.string.search_kw_notifications,
            ),
        )
        ServerAgent.entries.forEach { add(agent(it)) }
        add(
            server(
                ServerSettingsPage.DiscoverSliders,
                R.string.server_settings_sliders,
                Icons.Filled.ViewCarousel,
                R.string.search_kw_sliders,
            ),
        )
        add(
            server(
                ServerSettingsPage.Network,
                R.string.server_settings_network,
                Icons.Filled.Dns,
                R.string.search_kw_network,
            ),
        )
        add(
            server(
                ServerSettingsPage.Metadata,
                R.string.server_settings_metadata,
                Icons.Filled.Public,
                R.string.search_kw_metadata,
            ),
        )
        add(server(ServerSettingsPage.Cache, R.string.server_settings_cache, Icons.Filled.Cached, R.string.search_kw_cache))
        add(server(ServerSettingsPage.Logs, R.string.server_settings_logs, Icons.AutoMirrored.Filled.Article, R.string.search_kw_logs))
        add(server(ServerSettingsPage.About, R.string.settings_about, Icons.Filled.Info, R.string.search_kw_about))
        addAll(settingRowEntries)
        add(
            SettingEntry(
                SearchTarget.ThisApp,
                R.string.settings_group_app,
                Icons.Filled.PhoneAndroid,
                R.string.settings_group_app,
                R.string.search_kw_this_app,
            ),
        )
        add(
            SettingEntry(
                SearchTarget.Licences,
                R.string.licences_title,
                Icons.Filled.Description,
                R.string.settings_group_app,
                R.string.search_kw_licences,
            ),
        )
    }
