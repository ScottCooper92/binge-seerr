package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val LOADING = "loading"
private const val SETUP = "setup"
private const val HUB = "hub"
private const val PLACEHOLDER = "placeholder"

/**
 * Home under a real [NavDisplay] and list-detail strategy, as the connection resolves after the first
 * frame, which is what it does on every cold start.
 *
 * The entries show stand-in text. The real hub and setup take Hilt ViewModels, and this JVM suite has
 * no graph for them. What is under test is the shape [SeerrNavHost] is built on: a connection read
 * inside the content through a provider, the hub's list-pane metadata on a key of its own, and
 * [settleHome] swapping the root. The first test pins why that shape is needed at all.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h800dp")
class HomeRootSwapTest {
    @get:Rule
    val rule = createComposeRule()

    private val connection = MutableStateFlow<Boolean?>(null)

    /** The shape before this fix: one route, handed the connection as a value when its entry was built. */
    private fun EntryProviderScope<NavKey>.capturedHome(connected: Boolean?) {
        entry<HomeRoute> { Text(if (connected == true) HUB else LOADING) }
    }

    private fun EntryProviderScope<NavKey>.providedHome(connected: () -> Boolean?) {
        entry<HomeRoute> { Text(if (connected() == false) SETUP else LOADING) }
        entry<HubRoute>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { Text(PLACEHOLDER) })) {
            Text(if (connected() == true) HUB else LOADING)
        }
    }

    @Composable
    private fun Home(
        backStack: NavBackStack<NavKey>,
        captured: Boolean = false,
    ) {
        val connectedState = connection.collectAsStateWithLifecycle()
        val connected by connectedState
        val root = backStack.firstOrNull()
        if (!captured) LaunchedEffect(connected, root) { backStack.settleHome(connected) }
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies =
                listOf(rememberListDetailSceneStrategy<NavKey>(directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()))),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider =
                entryProvider {
                    if (captured) capturedHome(connected) else providedHome { connectedState.value }
                },
        )
    }

    private fun show(
        vararg keys: NavKey,
        captured: Boolean = false,
    ): NavBackStack<NavKey> {
        lateinit var backStack: NavBackStack<NavKey>
        rule.setContent {
            backStack = remember { NavBackStack(mutableStateListOf(*keys)) }
            Home(backStack, captured)
        }
        rule.waitForIdle()
        return backStack
    }

    private fun resolve(connected: Boolean) {
        connection.value = connected
        rule.waitForIdle()
    }

    private fun shows(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /**
     * Navigation 3 keeps the entry it built for a key. If this starts failing, the library has begun
     * rebuilding entries, and the provider and the second key may no longer be needed.
     */
    @Test
    fun `an entry that captured the connection keeps the spinner after it resolves`() {
        show(HomeRoute, captured = true)
        resolve(connected = true)

        assertEquals(true, shows(LOADING))
        assertEquals(false, shows(HUB))
    }

    /**
     * The bug in #277: the hub is the list pane, so the spinner it drew while the connection was
     * unresolved rendered beside the detail placeholder. A saved server restores the hub at the root,
     * so this was the state a cold start actually showed, not a frame.
     */
    @Test
    fun `a restored hub shows the spinner full width until the connection resolves`() {
        val backStack = show(HubRoute)

        assertEquals(listOf<NavKey>(HomeRoute), backStack.toList())
        assertEquals(true, shows(LOADING))
        assertEquals(false, shows(PLACEHOLDER))

        resolve(connected = true)

        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
        assertEquals(true, shows(HUB))
        assertEquals(true, shows(PLACEHOLDER))
    }

    @Test
    fun `a saved server brings the hub in beside the placeholder`() {
        val backStack = show(HomeRoute)
        assertEquals(true, shows(LOADING))

        resolve(connected = true)

        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
        assertEquals(true, shows(HUB))
        assertEquals(true, shows(PLACEHOLDER))
        assertEquals(false, shows(LOADING))
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun `on a compact window the hub takes the whole window`() {
        show(HomeRoute)
        resolve(connected = true)

        assertEquals(true, shows(HUB))
        assertEquals(false, shows(PLACEHOLDER))
    }

    @Test
    fun `nothing saved brings setup in place of the spinner`() {
        val backStack = show(HomeRoute)
        resolve(connected = false)

        assertEquals(listOf<NavKey>(HomeRoute), backStack.toList())
        assertEquals(true, shows(SETUP))
        assertEquals(false, shows(LOADING))
    }

    @Test
    fun `a disconnect takes the hub back to setup`() {
        val backStack = show(HomeRoute)
        resolve(connected = true)

        resolve(connected = false)

        assertEquals(listOf<NavKey>(HomeRoute), backStack.toList())
        assertEquals(true, shows(SETUP))
        assertEquals(false, shows(HUB))
    }
}
