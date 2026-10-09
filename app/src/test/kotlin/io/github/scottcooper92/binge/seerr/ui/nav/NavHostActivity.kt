package io.github.scottcooper92.binge.seerr.ui.nav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import dagger.hilt.android.AndroidEntryPoint
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.ui.HomeRoute
import io.github.scottcooper92.binge.seerr.ui.SeerrNavHost

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

    /** Cleared before the activity closes, so the composition is taken down while the looper is live (#663). */
    internal var shown by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (!shown) return@setContent
            val stack = rememberNavBackStack(*firstStack.toTypedArray())
            backStack = stack
            SeerrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SeerrNavHost(backStack = stack)
                }
            }
        }
    }

    companion object {
        /** The stack the next launch starts on. Saved state wins over it on a recreate, as it should. */
        internal var firstStack: List<NavKey> = listOf(HomeRoute)
    }
}
