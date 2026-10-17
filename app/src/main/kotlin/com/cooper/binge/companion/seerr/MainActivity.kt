package com.cooper.binge.companion.seerr

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import com.cooper.binge.companion.seerr.auth.ConnectionRestore
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.FeedbackPrefs
import com.cooper.binge.companion.seerr.feedback.ShakeToReportPrompt
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.LocalAnalytics
import com.cooper.binge.companion.seerr.telemetry.screenName
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.DeepLinkNavigator
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.SeerrNavHost
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.consent.ConsentGate
import com.cooper.binge.companion.seerr.ui.linkedRequestId
import com.cooper.binge.companion.seerr.ui.tv.TvSeerrShell
import com.cooper.binge.companion.seerr.ui.tv.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
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
        // Installed before super.onCreate, per the API's contract. Held until the Block Store restore,
        // which decides setup-versus-hub, has been tried; ConnectionRestore settles in a `finally`,
        // so this clears on every path, a failed probe included.
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
            // A television gets the D-pad shell, as Binge's MainActivity selects its own at runtime. It has no
            // back stack, so of the links it follows only a request's, opening its page over Home.
            if (LocalConfiguration.current.isTelevision()) {
                val linkedRequests = remember { deepLinks.backStacks.mapNotNull { it.linkedRequestId() } }
                CompositionLocalProvider(LocalAnalytics provides analytics) { TvSeerrShell(linkedRequests = linkedRequests) }
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
