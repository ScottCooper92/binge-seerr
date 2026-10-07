package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
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
private const val ISSUE = "issue 3"
private const val REQUESTS = "requests"
private const val REQUEST = "request 7"

/**
 * The detail pane under a real [NavDisplay], with the strategy and metadata [SeerrNavHost] uses and
 * Back delivered the way a gesture or a key delivers it: through the navigation-event dispatcher the
 * scene registers with. The entries show stand-in text, because the real screens take Hilt ViewModels.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h800dp")
class DetailPaneStackTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val dispatcher = NavigationEventDispatcher()
    private val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }

    /** The panes the window allows, standing in for a rotation; null keeps what the window qualifiers give. */
    private var partitions by mutableStateOf<Int?>(null)

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
                    sceneStrategies = listOf(rememberSeerrPaneStrategy(directive(), backStack)),
                    entryDecorators =
                        listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                    entryProvider =
                        entryProvider {
                            entry<HubRoute>(
                                metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { Text(DEFAULT) }),
                            ) { Text(HUB) }
                            entry<IssuesRoute>(metadata = DetailPane) { Text(ISSUES) }
                            entry<IssueDetailRoute>(metadata = DetailPane) { route -> Text("issue ${route.issueId}") }
                            entry<RequestsRoute>(metadata = DetailPane) { Text(REQUESTS) }
                            entry<RequestDetailRoute>(metadata = DetailPane) { route -> Text("request ${route.requestId}") }
                        },
                )
            }
        }
        rule.waitForIdle()
        return backStack
    }

    @Composable
    private fun directive() =
        calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).let { directive ->
            partitions?.let { directive.copy(maxHorizontalPartitions = it) } ?: directive
        }

    private fun rotate(panes: Int) {
        partitions = panes
        rule.waitForIdle()
    }

    private fun back() {
        input.backStarted(NavigationEvent(touchX = 0f, touchY = 0f, progress = 0f, swipeEdge = NavigationEvent.EDGE_NONE))
        input.backCompleted()
        rule.waitForIdle()
    }

    private fun onScreen() =
        listOf(HUB, DEFAULT, ISSUES, ISSUE, REQUESTS, REQUEST).filter {
            rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun `beside the hub, screens stack in the detail pane and Back walks down through them`() {
        val backStack = show(HubRoute, IssuesRoute, IssueDetailRoute(3))
        assertEquals(listOf(HUB, ISSUE), onScreen())

        back()
        assertEquals(listOf<NavKey>(HubRoute, IssuesRoute), backStack.toList())
        assertEquals(listOf(HUB, ISSUES), onScreen())

        back()
        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
        assertEquals(listOf(HUB, DEFAULT), onScreen())
    }

    /** Nothing is left to pop, so the scene does not claim Back: the system gets it and leaves the app. */
    @Test
    fun `beside the hub with only the default showing, Back is not taken`() {
        val backStack = show(HubRoute)
        assertEquals(listOf(HUB, DEFAULT), onScreen())

        back()

        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
        assertEquals(listOf(HUB, DEFAULT), onScreen())
    }

    /** Popping it would land on its own placeholder, which looks the same, so Back leaves as it does from there (#815). */
    @Test
    fun `beside the hub with the default section chosen, Back is not taken`() {
        val backStack = show(HubRoute, RequestsRoute)
        assertEquals(listOf(HUB, REQUESTS), onScreen())

        back()

        assertEquals(listOf<NavKey>(HubRoute, RequestsRoute), backStack.toList())
        assertEquals(listOf(HUB, REQUESTS), onScreen())
    }

    @Test
    fun `beside the hub, Back from what the default section opened returns to it`() {
        val backStack = show(HubRoute, RequestsRoute, RequestDetailRoute(7))
        assertEquals(listOf(HUB, REQUEST), onScreen())

        back()

        assertEquals(listOf<NavKey>(HubRoute, RequestsRoute), backStack.toList())
        assertEquals(listOf(HUB, REQUESTS), onScreen())
    }

    /** Rotating to one pane shows what was on top, not the hub, and rotating back restores the pair (#815). */
    @Test
    fun `a rotation keeps what the pane beside the hub was showing`() {
        val stacks =
            mapOf(
                listOf(HubRoute, RequestsRoute) to REQUESTS,
                listOf(HubRoute, RequestsRoute, RequestDetailRoute(7)) to REQUEST,
                listOf(HubRoute, IssuesRoute) to ISSUES,
                listOf(HubRoute, IssuesRoute, IssueDetailRoute(3)) to ISSUE,
            )
        val backStack = show(HubRoute)
        stacks.forEach { (stack, top) ->
            backStack.clear()
            backStack.addAll(stack)
            rule.waitForIdle()
            assertEquals("$stack", listOf(HUB, top), onScreen())

            rotate(panes = 1)
            assertEquals("$stack", listOf(top), onScreen())

            rotate(panes = 2)
            assertEquals("$stack", listOf(HUB, top), onScreen())
        }
    }

    /** Nothing chosen, so one pane shows the hub alone: the user never opened anything. */
    @Test
    fun `a rotation with only the placeholder showing leaves the hub`() {
        show(HubRoute)

        rotate(panes = 1)

        assertEquals(listOf(HUB), onScreen())
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun `on a narrow window each screen takes the window and Back walks down to the hub`() {
        val backStack = show(HubRoute, IssuesRoute, IssueDetailRoute(3))
        assertEquals(listOf(ISSUE), onScreen())

        back()
        assertEquals(listOf(ISSUES), onScreen())

        back()
        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
        assertEquals(listOf(HUB), onScreen())
    }
}
