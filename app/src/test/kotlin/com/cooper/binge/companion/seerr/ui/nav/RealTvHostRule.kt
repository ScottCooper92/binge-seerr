package com.cooper.binge.companion.seerr.ui.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.cooper.binge.companion.seerr.MainActivity
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.telemetry.TelemetryPrefs
import com.cooper.binge.companion.seerr.ui.DeepLinkNavigator
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/** Long enough for a cold start's first reads; a pass takes a fraction of it. */
private const val AWAIT_MILLIS = 10_000L
private const val POLL_MILLIS = 25L

/**
 * The television counterpart of [RealNavHostRule]: it launches the real [MainActivity] on a television configuration, so
 * the whole path is the app's own, from the activity through `TvSeerrShell` to `TvConnectedShell`, against the app's Hilt
 * graph with only the device's keys replaced ([TestDeviceKeysModule]). The server is a [ScriptedSeerr].
 *
 * The test class carries `@HiltAndroidTest`, a `@Config` with `HiltTestApplication` and a `television` qualifier, and holds
 * this as its only rule: `@get:Rule internal val host = RealTvHostRule(this)`. A link is either the launch intent's data,
 * as a notification's tap carries it, or sent to the running app with [sendLink], as a host's hand-off delivers it.
 */
internal class RealTvHostRule(
    test: Any,
) : TestRule {
    private val hilt = HiltAndroidRule(test)
    private val folder = TemporaryFolder()
    val compose: ComposeTestRule = createEmptyComposeRule()
    val seerr by lazy { ScriptedSeerr(folder) }
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Serves the server as an admin on a current Seerr, and answers the analytics question, so nothing stands before Home. */
    fun startServer(permissions: Int = ADMIN) {
        initializeWorkManager()
        seerr.start()
        seerr.viewer(id = 1, permissions = permissions)
        runBlocking {
            // The stores are DataStores the process keeps between tests, so a connection an earlier test saved is gone here.
            graph().credentials().clear()
            graph().telemetry().setAnalyticsGranted(false)
        }
    }

    /**
     * The app turns the platform's initializer off and starts WorkManager from its own application, which a Hilt test
     * application replaces. Settings asks it when a notification is next due, so it is started here, once per process.
     */
    private fun initializeWorkManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (!WorkManager.isInitialized()) WorkManager.initialize(context, Configuration.Builder().build())
    }

    /** Saves a connection to [seerr]: the app, if it is running, moves from Setup to Home. */
    fun saveConnection() {
        val saved = runBlocking { graph().credentials().save(SeerrCredentials(seerr.server.url("/").toString(), SeerrAuth.ApiKey("k3y"))) }
        check(saved) { "the connection was not saved" }
    }

    /** Starts the app, with [link] as the launch intent's data when there is one. */
    fun launch(link: String? = null) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java).apply { link?.let { data = Uri.parse(it) } }
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    /** A link handed to the running app, through the same channel the activity feeds. */
    fun sendLink(link: String) = graph().deepLinks().open(link)

    fun string(
        @StringRes id: Int,
    ): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** Whether a node whose text or description includes [text] is on screen now; a tab in the top bar is described, not worded. */
    fun isShowing(text: String): Boolean =
        settled()
            .onAllNodes(hasText(text, substring = true) or hasContentDescription(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    /** Whether a node worded exactly [text] is on screen now, which a description of the same words does not count for. */
    fun isWorded(text: String): Boolean = settled().onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Waits, in real time, for [text]: what the screens show next comes from the server, over real sockets, or from a read
     * on another thread. The thread sleeps between looks, as the compose rule's own wait does not let that work land.
     */
    fun awaitShowing(text: String) = await("\"$text\" on screen") { isShowing(text) }

    /** Waits, in real time, until a node is worded exactly [text]. */
    fun awaitWorded(text: String) = await("\"$text\" worded on screen") { isWorded(text) }

    private fun await(
        what: String,
        done: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS)
        while (!done()) {
            check(System.nanoTime() < deadline) {
                "$what: still not there after $AWAIT_MILLIS ms\n" +
                    compose.onRoot(useUnmergedTree = true).printToString(200)
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    /** The compose rule once the queued effects have run, so a read does not see the screen from before a change. */
    private fun settled(): ComposeTestRule = compose.also { it.waitForIdle() }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        RuleChain
            .outerRule(hilt)
            .around(folder)
            .around(compose)
            .apply(
                object : Statement() {
                    override fun evaluate() {
                        // The test's own failure wins over one in the take-down.
                        val outcome = runCatching { base.evaluate() }
                        val takenDown = runCatching { takeDown() }
                        outcome.getOrThrow()
                        takenDown.getOrThrow()
                    }
                },
                description,
            )

    private fun takeDown() {
        scenario?.close()
        scenario = null
        compose.waitForIdle()
        seerr.close()
    }

    private fun graph(): TvHostGraph =
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), TvHostGraph::class.java)

    /** What the fixture reads from the app's graph itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TvHostGraph {
        fun credentials(): CredentialStore

        fun telemetry(): TelemetryPrefs

        fun deepLinks(): DeepLinkNavigator
    }
}
