package io.github.scottcooper92.binge.seerr.ui

import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import kotlinx.serialization.Serializable

/**
 * The app's own screens, as Navigation 3 keys. Serializable so the back stack is saved across
 * process death. The advanced picker is not one: Binge starts it as an Activity for a result.
 */
sealed interface SeerrRoute : NavKey

/** The start destination: the hub when a server is connected, setup when none is. */
@Serializable
data object HomeRoute : SeerrRoute

/** Settings: the connection and the admin's read-only view of the server. */
@Serializable
data object SettingsRoute : SeerrRoute

/** The setup form on the live connection; pops itself once new credentials are saved. */
@Serializable
data object EditConnectionRoute : SeerrRoute

/** One of the hub's manage sections; its screen arrives with its phase. */
@Serializable
data class SectionRoute(
    val section: HubSection,
) : SeerrRoute
