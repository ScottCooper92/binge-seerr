package io.github.scottcooper92.binge.seerr.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.test.core.app.ApplicationProvider
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HUB = "hub"
private const val DEFAULT = "default section"
private const val ISSUES = "issues"
private const val REQUESTS = "requests"
private const val ISSUE_TAG = "issue"
private const val TOLERANCE = 1f

/**
 * A landscape tablet's three panes under a real [NavDisplay], with the strategy and metadata [SeerrNavHost] uses and
 * Back delivered through the navigation-event dispatcher, as a gesture or a key delivers it (#1110). The entries show
 * stand-in text, so this pins the scene on its own; `SeerrNavHostJourneysTest` drives the real entries.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class ThreePaneSceneTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val dispatcher = NavigationEventDispatcher()
    private val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }

    private val nothingOpen = ApplicationProvider.getApplicationContext<Context>().getString(R.string.three_pane_nothing_open_title)

    private fun show(vararg keys: NavKey): NavBackStack<NavKey> {
        lateinit var backStack: NavBackStack<NavKey>
        val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }
        rule.setContent {
            backStack = remember { NavBackStack(mutableStateListOf(*keys)) }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategies =
                        listOf(
                            rememberSeerrPaneStrategy(
                                calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
                                backStack,
                                threePane = true,
                                defaultSection = { Text(DEFAULT, Modifier.testTag(DEFAULT)) },
                            ),
                        ),
                    entryDecorators =
                        listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                    entryProvider =
                        entryProvider {
                            entry<HubRoute>(
                                metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { Text(DEFAULT) }) + ThreePaneHub,
                            ) { Text(HUB, Modifier.testTag(HUB)) }
                            entry<IssuesRoute>(metadata = DetailPane + ThreePaneSection) { Text(ISSUES, Modifier.testTag(ISSUES)) }
                            entry<IssueDetailRoute>(metadata = DetailPane) { route ->
                                Text("issue ${route.issueId}", Modifier.testTag(ISSUE_TAG))
                            }
                            entry<RequestsRoute>(metadata = DetailPane + ThreePaneSection) { Text(REQUESTS) }
                            entry<RequestDetailRoute>(metadata = DetailPane) { route -> Text("request ${route.requestId}") }
                            entry<UserDetailRoute>(metadata = DetailPane) { route -> Text("user ${route.userId}") }
                        },
                )
            }
        }
        rule.waitForIdle()
        return backStack
    }

    private fun back() {
        input.backStarted(NavigationEvent(touchX = 0f, touchY = 0f, progress = 0f, swipeEdge = NavigationEvent.EDGE_NONE))
        input.backCompleted()
        rule.waitForIdle()
    }

    private fun onScreen(): List<String> =
        listOf(HUB, DEFAULT, ISSUES, REQUESTS, "issue 3", "issue 4", "request 7", "user 2", nothingOpen).filter {
            rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun `the hub alone has the default section beside it and nothing open`() {
        show(HubRoute)

        assertEquals(listOf(HUB, DEFAULT, nothingOpen), onScreen())
    }

    @Test
    fun `a section with nothing open shows the placeholder in the third pane`() {
        show(HubRoute, IssuesRoute)

        assertEquals(listOf(HUB, ISSUES, nothingOpen), onScreen())
    }

    @Test
    fun `a section and what it opened sit side by side, and Back closes the item`() {
        val backStack = show(HubRoute, IssuesRoute, IssueDetailRoute(3))
        assertEquals(listOf(HUB, ISSUES, "issue 3"), onScreen())

        back()

        assertEquals(listOf<NavKey>(HubRoute, IssuesRoute), backStack.toList())
        assertEquals(listOf(HUB, ISSUES, nothingOpen), onScreen())
    }

    @Test
    fun `what an item opens takes the third pane, and Back returns to the item`() {
        val backStack = show(HubRoute, RequestsRoute, RequestDetailRoute(7), UserDetailRoute(2))
        assertEquals(listOf(HUB, REQUESTS, "user 2"), onScreen())

        back()

        assertEquals(listOf<NavKey>(HubRoute, RequestsRoute, RequestDetailRoute(7)), backStack.toList())
        assertEquals(listOf(HUB, REQUESTS, "request 7"), onScreen())
    }

    @Test
    fun `opening from the list replaces the open item rather than stacking above it`() {
        val backStack = show(HubRoute, IssuesRoute, IssueDetailRoute(3), UserDetailRoute(2))

        backStack.openBeside(IssueDetailRoute(4))
        rule.waitForIdle()

        assertEquals(listOf<NavKey>(HubRoute, IssuesRoute, IssueDetailRoute(4)), backStack.toList())
        assertEquals(listOf(HUB, ISSUES, "issue 4"), onScreen())
    }

    /** Popping it would show the placeholder, which looks the same, so Back leaves the app (#815). */
    @Test
    fun `with the default section on the stack and nothing open, Back is not taken`() {
        val backStack = show(HubRoute, RequestsRoute)

        back()

        assertEquals(listOf<NavKey>(HubRoute, RequestsRoute), backStack.toList())
    }

    /** Each stand-in starts at its pane's start, so equal steps between the starts are equal panes. */
    @Test
    fun `the three panes are the same width`() {
        show(HubRoute, IssuesRoute, IssueDetailRoute(3))

        val hub = start(HUB)
        val section = start(ISSUES)
        val item = start(ISSUE_TAG)
        assertEquals(section - hub, item - section, TOLERANCE)
    }

    private fun start(tag: String) =
        rule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .getBoundsInRoot()
            .left.value
}
