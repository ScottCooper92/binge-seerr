package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
    val rule = createComposeRule()

    private val dispatcher = NavigationEventDispatcher()
    private val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }

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
                        listOf(rememberSeerrPaneStrategy(calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()))),
                    entryDecorators =
                        listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                    entryProvider =
                        entryProvider {
                            entry<HubRoute>(
                                metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { Text(DEFAULT) }),
                            ) { Text(HUB) }
                            entry<IssuesRoute>(metadata = DetailPane) { Text(ISSUES) }
                            entry<IssueDetailRoute>(metadata = DetailPane) { route -> Text("issue ${route.issueId}") }
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

    private fun onScreen() = listOf(HUB, DEFAULT, ISSUES, ISSUE).filter { rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }

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
