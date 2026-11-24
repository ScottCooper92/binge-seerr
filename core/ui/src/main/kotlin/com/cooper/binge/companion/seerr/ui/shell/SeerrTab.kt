package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.booleanResource
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.core.ui.R
import kotlinx.serialization.Serializable

/** The tabs of the discovery shell, in the order the bar shows them. */
enum class SeerrTab(
    val icon: ImageVector,
    val testTag: String,
) {
    Movies(Icons.Filled.Movie, "tab_movies"),
    Tv(Icons.Filled.Tv, "tab_tv"),
    Discover(Icons.Filled.Explore, "tab_discover"),
    Search(Icons.Filled.Search, "tab_search"),
    Settings(Icons.Filled.Settings, "tab_settings"),
}

/** The root of each tab's own stack. Anything a tab opens goes on the app's stack above the shell, not on these. */
@Serializable
data object MoviesRoot : NavKey

@Serializable
data object TvRoot : NavKey

@Serializable
data object SearchRoot : NavKey

@Serializable
data object SettingsRoot : NavKey

/** Whether Discover is a tab: on a tablet-width window (sw600dp) only, as in Binge. On a phone the grids open from the hubs. */
@Composable
@ReadOnlyComposable
fun showDiscoverTab(): Boolean = booleanResource(R.bool.shell_show_discover)
