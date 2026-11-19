package com.cooper.binge.companion.seerr.ui

import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistItem
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
import kotlinx.serialization.Serializable

/**
 * The app's own screens, as Navigation 3 keys. Serializable so the back stack is saved across
 * process death. The advanced-request picker is not one: Binge renders it itself from the advanced-request RPCs.
 */
sealed interface SeerrRoute : NavKey

/** The start destination: the spinner while the connection is worked out, then setup if no server is saved. */
@Serializable
data object HomeRoute : SeerrRoute

/** The hub, which replaces [HomeRoute] at the root once a server is connected. See [settleHome]. */
@Serializable
data object HubRoute : SeerrRoute

/**
 * The tab bar over each tab root, for a connected phone with the discovery flag on. It stands in for [HubRoute] at the root
 * (see [settleHome]), and what a tab opens goes on the stack above it, so it covers the bar.
 */
@Serializable
data object ShellRoute : SeerrRoute

/** The requests browser. */
@Serializable
data object RequestsRoute : SeerrRoute

/** The issues browser. */
@Serializable
data object IssuesRoute : SeerrRoute

/** One issue as a page: its thread. */
@Serializable
data class IssueDetailRoute(
    val issueId: Int,
) : SeerrRoute

/** The blocklist browser. */
@Serializable
data object BlocklistRoute : SeerrRoute

/**
 * One blocked title as a page. [item] arrives whole off the row that opened it — there is no `GET`
 * for a single blocklist entry — and [canManage] is the browser's own gate, current as of the tap.
 */
@Serializable
data class BlocklistDetailRoute(
    val item: BlocklistItem,
    val canManage: Boolean,
) : SeerrRoute

/** The users browser. */
@Serializable
data object UsersRoute : SeerrRoute

/** One user as a page. */
@Serializable
data class UserDetailRoute(
    val userId: Int,
) : SeerrRoute

/** One user's settings: the index of the pages this viewer may open. */
@Serializable
data class UserSettingsRoute(
    val userId: Int,
) : SeerrRoute

/** One of a user's settings pages. */
@Serializable
data class UserSettingsPageRoute(
    val userId: Int,
    val page: UserSettingsPage,
    /** The row to scroll to and wash, by [com.cooper.binge.companion.seerr.ui.search.SettingRows] key; null opens the page at the top. */
    val row: String? = null,
) : SeerrRoute

/** One of the server's own settings pages, for a user who manages settings. */
@Serializable
data class ServerSettingsPageRoute(
    val page: ServerSettingsPage,
    /** The row to scroll to and wash, by [com.cooper.binge.companion.seerr.ui.search.SettingRows] key; null opens the page at the top. */
    val row: String? = null,
) : SeerrRoute

/** One Radarr or Sonarr instance; a null [id] is a new one. */
@Serializable
data class DvrInstanceRoute(
    val type: ServiceType,
    val id: Int?,
) : SeerrRoute

/** One custom discover slider; a null [id] is a new one. */
@Serializable
data class DiscoverSliderRoute(
    val id: Int?,
) : SeerrRoute

/** One notification agent's settings page. */
@Serializable
data class NotificationAgentRoute(
    val agent: ServerAgent,
) : SeerrRoute

/** One override rule; a null [id] is a new one. */
@Serializable
data class OverrideRuleRoute(
    val id: Int?,
) : SeerrRoute

/** One request as a page. */
@Serializable
data class RequestDetailRoute(
    val requestId: Int,
) : SeerrRoute

/** A title as a page, opened from a card in the discovery tabs: a film's or a series'. */
@Serializable
data class TitleDetailRoute(
    val kind: TitleKind,
    val tmdbId: Int,
) : SeerrRoute

/** A collection of films as a page, opened from the collection rail on a film's page. */
@Serializable
data class CollectionRoute(
    val id: Int,
) : SeerrRoute

/** A person: their profile and the titles they are in, opened from a title page, the cast list or a search result. */
@Serializable
data class PersonDetailRoute(
    val personId: Int,
) : SeerrRoute

/** The whole cast and crew of a title, opened from its page. */
@Serializable
data class TitleCreditsRoute(
    val kind: TitleKind,
    val tmdbId: Int,
) : SeerrRoute

/** Every episode of every season of a show, opened from the show's page at [seasonNumber], the season it had chosen. [seriesName] is the show's name for the bar. */
@Serializable
data class SeriesEpisodesRoute(
    val tmdbId: Int,
    val seriesName: String,
    val seasonNumber: Int,
) : SeerrRoute

/** Settings: the connection and the admin's read-only view of the server. */
@Serializable
data object SettingsRoute : SeerrRoute

/** The settings search: the whole window, over the hub. A result opens its page above the search, which stays beneath it. */
@Serializable
data object SettingsSearchRoute : SeerrRoute

/** This app's own page: Binge, feedback, what it reports, and About. The one section every viewer has. */
@Serializable
data class ThisAppRoute(
    /** The row to scroll to and wash, by [com.cooper.binge.companion.seerr.ui.search.SettingRows] key; null opens the page at the top. */
    val row: String? = null,
) : SeerrRoute

/** The open-source licences of the libraries the app ships, opened from This app. */
@Serializable
data object LicencesRoute : SeerrRoute

/** The app's own log, with Copy and Share, opened from This app. */
@Serializable
data object DiagnosticsRoute : SeerrRoute

/** The setup form on the live connection; pops itself once new credentials are saved. */
@Serializable
data object EditConnectionRoute : SeerrRoute

/**
 * The paged grid a hub row's "see all" opens. [preset] is the row, which becomes the grid's starting filter, and [title] is its
 * name for the bar and the filter's chip. Opened over the tab bar, like every other page a tab opens.
 */
@Serializable
data class DiscoverGridRoute(
    val kind: TitleKind,
    val preset: GridPreset,
    val title: String? = null,
    val filters: DiscoverFilters? = null,
    val sort: DiscoverSort? = null,
) : SeerrRoute
