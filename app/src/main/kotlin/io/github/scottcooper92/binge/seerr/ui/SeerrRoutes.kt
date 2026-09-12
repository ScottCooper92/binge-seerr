package io.github.scottcooper92.binge.seerr.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The app's own screens, as Navigation 3 keys. Serializable so the back stack is saved across
 * process death. The advanced picker is not one: Binge starts it as an Activity for a result.
 */
sealed interface SeerrRoute : NavKey

/** Connect a server, or see and disconnect the connected one. The start destination. */
@Serializable
data object SetupRoute : SeerrRoute
