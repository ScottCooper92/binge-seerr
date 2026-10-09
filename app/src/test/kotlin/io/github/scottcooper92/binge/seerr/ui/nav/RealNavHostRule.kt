package io.github.scottcooper92.binge.seerr.ui.nav

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.components.SingletonComponent
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.ui.HomeRoute
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.runBlocking
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Long enough for a cold start's first reads; a pass takes a fraction of it. */
private const val AWAIT_MILLIS = 10_000L

/**
 * Hosts the real [io.github.scottcooper92.binge.seerr.ui.SeerrNavHost] on the JVM, against the app's
 * own Hilt graph. Only the device's keys are replaced ([TestDeviceKeysModule]); the connection, the
 * stores and every ViewModel are the ones the app ships. The server is a [ScriptedSeerr].
 *
 * The test class carries `@HiltAndroidTest` and `@Config(application = HiltTestApplication::class)`,
 * and holds this as its only rule: `@get:Rule internal val host = RealNavHostRule(this)`. The compose
 * rule inside is the empty one, because the activity sets its own content; the rule takes that content
 * down before the activity closes, as `createSeerrComposeRule` does (#663).
 */
internal class RealNavHostRule(
    test: Any,
) : TestRule {
    private val hilt = HiltAndroidRule(test)
    private val folder = TemporaryFolder()
    val compose: ComposeTestRule = createEmptyComposeRule()
    val seerr by lazy { ScriptedSeerr(folder) }
    private var scenario: ActivityScenario<NavHostActivity>? = null

    /** The activity on screen now; a new instance after [rotate]. */
    val activity: NavHostActivity
        get() {
            lateinit var current: NavHostActivity
            checkNotNull(scenario) { "launch() first" }.onActivity { current = it }
            return current
        }

    /** Saves a connection to [seerr], served as an admin on a current Seerr, before anything reads it. */
    fun connect(permissions: Int = ADMIN) {
        seerr.start()
        seerr.viewer(id = 1, permissions = permissions)
        val saved = runBlocking { graph().credentials().save(SeerrCredentials(seerr.server.url("/").toString(), SeerrAuth.ApiKey("k3y"))) }
        check(saved) { "the connection was not saved" }
    }

    /** Starts the host on [stack]. A second launch closes the first: a cold start of the app over the same graph. */
    fun launch(vararg stack: NavKey) {
        close()
        NavHostActivity.firstStack = stack.toList()
        scenario = ActivityScenario.launch(NavHostActivity::class.java)
        compose.waitForIdle()
    }

    fun string(
        @StringRes id: Int,
    ): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /**
     * Whether a node reading [text] is on screen now. By default a merged node whose text only includes it counts too, as
     * a list row's title does; [exactly] asks for a text that is [text] and nothing more, as a button's label is.
     */
    fun isShowing(
        text: String,
        exactly: Boolean = false,
    ): Boolean = compose.onAllNodes(hasText(text, substring = !exactly)).fetchSemanticsNodes().isNotEmpty()

    /** Waits, in real time, for [text]: what the screens show next comes from the server, over real sockets. */
    fun awaitShowing(text: String) = compose.waitUntil("\"$text\" on screen", AWAIT_MILLIS) { isShowing(text) }

    /** A rotation as a device has one: the window's [qualifiers] change and the activity is recreated. */
    fun rotate(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        checkNotNull(scenario).recreate()
        compose.waitForIdle()
    }

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
                        registerActivity()
                        // The test's own failure wins over one in the take-down.
                        val outcome = runCatching { base.evaluate() }
                        val takenDown = runCatching { takeDown() }
                        outcome.getOrThrow()
                        takenDown.getOrThrow()
                    }
                },
                description,
            )

    /**
     * Robolectric resolves an activity through the package manager, and [NavHostActivity] is a test
     * class, in no manifest. Declaring it here keeps it out of the debug APK.
     */
    private fun registerActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = NavHostActivity::class.java.name
                packageName = context.packageName
            },
        )
    }

    private fun takeDown() {
        close()
        NavHostActivity.firstStack = listOf(HomeRoute)
        seerr.close()
    }

    private fun close() {
        scenario?.let { launched ->
            launched.onActivity { it.shown = false }
            compose.waitForIdle()
            launched.close()
        }
        scenario = null
    }

    private fun graph(): NavHostGraph =
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), NavHostGraph::class.java)

    /** What the fixture reads from the app's graph itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NavHostGraph {
        fun credentials(): CredentialStore
    }
}
