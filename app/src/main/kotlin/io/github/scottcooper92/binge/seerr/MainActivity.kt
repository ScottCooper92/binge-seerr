package io.github.scottcooper92.binge.seerr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import dagger.hilt.android.AndroidEntryPoint
import io.github.scottcooper92.binge.seerr.auth.ConnectionRestore
import io.github.scottcooper92.binge.seerr.feedback.BugReportLinks
import io.github.scottcooper92.binge.seerr.feedback.FeedbackPrefs
import io.github.scottcooper92.binge.seerr.feedback.ShakeToReportPrompt
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.LocalAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.screenName
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.ui.DeepLinkNavigator
import io.github.scottcooper92.binge.seerr.ui.HomeRoute
import io.github.scottcooper92.binge.seerr.ui.SeerrNavHost
import io.github.scottcooper92.binge.seerr.ui.SeerrRoute
import io.github.scottcooper92.binge.seerr.ui.consent.ConsentGate
import io.github.scottcooper92.binge.seerr.ui.tv.TvSeerrShell
import io.github.scottcooper92.binge.seerr.ui.tv.isTelevision
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

private const val KEY_CONSUMED_LINK = "consumed_link"

/**
 * The app's own UI, one Navigation 3 host wearing Binge's theme so the two read as one product.
 * Home is the start destination, setup or the hub by whether a server is saved; the sections push
 * above it. A notification's link replaces the stack with its own, on a cold start and a warm one.
 * A television gets its own shell under the TV theme, bound to the same ViewModels.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var deepLinks: DeepLinkNavigator

    /** Only read for the splash's hold condition; the screens observe it through HomeViewModel. */
    @Inject
    lateinit var connectionRestore: ConnectionRestore

    @Inject
    lateinit var feedbackPrefs: FeedbackPrefs

    @Inject
    lateinit var bugReportLinks: BugReportLinks

    @Inject
    lateinit var analytics: Analytics

    private var consumedLink: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Installed before super.onCreate, per the API's contract, and held until the Block Store
        // restore has been tried: that answer is what decides setup-versus-hub, so dismissing any
        // earlier would hand the splash to the loading screen — the frame it exists to replace.
        // SeerrApp starts the restore and ConnectionRestore settles in a `finally`, so this
        // condition clears on every path including a failed probe.
        installSplashScreen().setKeepOnScreenCondition { !connectionRestore.settled.value }
        super.onCreate(savedInstanceState)
        // Keyed on the link, not on the saved state: after process death the launch intent is still
        // the notification's, and a rotation must not open it a second time.
        consumedLink = savedInstanceState?.getString(KEY_CONSUMED_LINK)
        val link = intent?.dataString
        if (link != null && link != consumedLink) {
            deepLinks.open(link)
            consumedLink = link
        }
        drawEdgeToEdge()
        setContent {
            // A television gets the D-pad shell, as Binge's MainActivity selects its own at runtime; the
            // notification links push phone routes, which the TV shell grows into with a later phase.
            if (LocalConfiguration.current.isTelevision()) {
                CompositionLocalProvider(LocalAnalytics provides analytics) { TvSeerrShell() }
                return@setContent
            }
            val backStack = rememberNavBackStack(HomeRoute)
            LaunchedEffect(backStack) {
                snapshotFlow { backStack.lastOrNull() as? SeerrRoute }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { analytics.screen(it.screenName()) }
            }
            LaunchedEffect(backStack) {
                deepLinks.backStacks.collect { routes ->
                    backStack.clear()
                    backStack.addAll(routes)
                }
            }
            // Phone only: a television has no accelerometer, and reports a bug from Settings instead.
            val shakeToReport by feedbackPrefs.shakeToReport.collectAsStateWithLifecycle(initialValue = false)
            SeerrTheme {
                ShakeToReportPrompt(enabled = shakeToReport, bugReportUrl = bugReportLinks::url)
                // The window's own background (the platform default, since Theme.Seerr sets none)
                // doesn't match this, so a two-pane gutter would otherwise show through as a
                // different colour than the panes it sits between.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConsentGate { SeerrNavHost(backStack = backStack) }
                }
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
