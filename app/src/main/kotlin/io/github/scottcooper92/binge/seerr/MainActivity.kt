package io.github.scottcooper92.binge.seerr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.theme.BingeExpressiveTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.scottcooper92.binge.seerr.ui.DeepLinkNavigator
import io.github.scottcooper92.binge.seerr.ui.HomeRoute
import io.github.scottcooper92.binge.seerr.ui.SeerrNavHost
import javax.inject.Inject

private const val KEY_CONSUMED_LINK = "consumed_link"

/**
 * The app's own UI, one Navigation 3 host wearing Binge's theme so the two read as one product.
 * Home is the start destination, setup or the hub by whether a server is saved; the sections push
 * above it. A notification's link replaces the stack with its own, on a cold start and a warm one.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var deepLinks: DeepLinkNavigator

    private var consumedLink: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keyed on the link, not on the saved state: after process death the launch intent is still
        // the notification's, and a rotation must not open it a second time.
        consumedLink = savedInstanceState?.getString(KEY_CONSUMED_LINK)
        val link = intent?.dataString
        if (link != null && link != consumedLink) {
            deepLinks.open(link)
            consumedLink = link
        }
        setContent {
            val backStack = rememberNavBackStack(HomeRoute)
            LaunchedEffect(backStack) {
                deepLinks.backStacks.collect { routes ->
                    backStack.clear()
                    backStack.addAll(routes)
                }
            }
            BingeExpressiveTheme {
                SeerrNavHost(backStack = backStack)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinks.open(intent.dataString)
        consumedLink = intent.dataString
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        consumedLink?.let { outState.putString(KEY_CONSUMED_LINK, it) }
    }
}
