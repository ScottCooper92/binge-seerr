package com.cooper.binge.companion.seerr.ui.nav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.SeerrNavHost
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import com.cooper.binge.companion.seerr.ui.shell.TabShellState
import com.cooper.binge.companion.seerr.ui.shell.rememberSeerrTabs
import dagger.hilt.android.AndroidEntryPoint

/**
 * The real [SeerrNavHost], themed and hosted the way `MainActivity` hosts it, under a Hilt activity so
 * every `hiltViewModel()` below it resolves against the app's own graph. The content is set in
 * [onCreate], so a recreate is a real one: the back stack comes back through saved state, as it does on
 * a device.
 */
@AndroidEntryPoint
class NavHostActivity : ComponentActivity() {
    /** The stack as it stands; set by the composition, for a test to read or push onto. */
    lateinit var backStack: NavBackStack<NavKey>
        private set

    /** The shell's state; set by the composition, so a test can apply a link as `MainActivity` does. */
    lateinit var tabs: TabShellState<SeerrTab>
        private set

    /** Cleared before the activity closes, so the composition is taken down while the looper is live (#663). */
    internal var shown by mutableStateOf(true)

    /** How many times the host asked to leave, as `MainActivity` does by finishing. */
    internal var exits = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (!shown) return@setContent
            val stack = rememberNavBackStack(*firstStack.toTypedArray())
            backStack = stack
            val tabs = rememberSeerrTabs(discovery = discoveryEnabled)
            this.tabs = tabs
            // A link's chain sits above the Settings tab, as MainActivity's openLink shows it; a plain launch opens on the start tab.
            remember { if (savedInstanceState == null && firstStack.size > 1 && discoveryEnabled) tabs.show(SeerrTab.Settings) }
            SeerrTheme {
                CompositionLocalProvider(LocalDiscoveryEnabled provides discoveryEnabled) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        SeerrNavHost(backStack = stack, onExit = ::leave, tabs = tabs)
                    }
                }
            }
        }
    }

    private fun leave() {
        exits++
        finish()
    }

    companion object {
        /** The stack the next launch starts on. Saved state wins over it on a recreate, as it should. */
        internal var firstStack: List<NavKey> = listOf(HomeRoute)

        /** Whether the discovery flag is on for the next launch; off, as the app ships it. */
        internal var discoveryEnabled: Boolean = false
    }
}
