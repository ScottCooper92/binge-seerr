package io.github.scottcooper92.binge.seerr.ui

import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserSettingsPage
import kotlinx.serialization.Serializable

/**
 * The app's own screens, as Navigation 3 keys. Serializable so the back stack is saved across
 * process death. The advanced picker is not one: Binge starts it as an Activity for a result.
 */
sealed interface SeerrRoute : NavKey

/** The start destination: the hub when a server is connected, setup when none is. */
@Serializable
data object HomeRoute : SeerrRoute

/** Connect a server. Reached from the hub's Disconnect, or as the home while nothing is saved. */
@Serializable
data object SetupRoute : SeerrRoute

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
) : SeerrRoute

/** One request as a page. */
@Serializable
data class RequestDetailRoute(
    val requestId: Int,
) : SeerrRoute

/** Settings: the connection and the admin's read-only view of the server. */
@Serializable
data object SettingsRoute : SeerrRoute

/** The setup form on the live connection; pops itself once new credentials are saved. */
@Serializable
data object EditConnectionRoute : SeerrRoute

/** One of the hub's manage sections whose screen arrives with a later phase. */
@Serializable
data class SectionRoute(
    val section: HubSection,
) : SeerrRoute
