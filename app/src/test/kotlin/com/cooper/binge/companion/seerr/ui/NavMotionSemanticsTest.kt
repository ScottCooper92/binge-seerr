package com.cooper.binge.companion.seerr.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Serializable
private data class Under(
    val id: Int = 1,
) : NavKey

@Serializable
private data class Over(
    val id: Int = 2,
) : NavKey

/**
 * Which entry's metadata Navigation 3 reads for a push, a pop and a predictive pop. The wiring of the shared motion puts a set on
 * an entry by what that entry is, so it has to know whether the spec is read from the screen coming in or from the one going out.
 * Both entries carry recording specs and the test reads off whose fired.
 */
@RunWith(RobolectricTestRunner::class)
class NavMotionSemanticsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val dispatcher = NavigationEventDispatcher()
    private val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
    private val fired = mutableListOf<String>()

    private fun record(
        who: String,
        which: String,
    ): ContentTransform {
        fired += "$who:$which"
        return ContentTransform(EnterTransition.None, ExitTransition.None)
    }

    private fun spec(who: String) =
        NavDisplay.transitionSpec { record(who, "forward") } +
            NavDisplay.popTransitionSpec { record(who, "pop") } +
            NavDisplay.predictivePopTransitionSpec { _ -> record(who, "predictive") }

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
                    entryProvider =
                        entryProvider {
                            entry<Under>(metadata = spec("under")) { Text("under") }
                            entry<Over>(metadata = spec("over")) { Text("over") }
                        },
                )
            }
        }
        rule.waitForIdle()
        fired.clear()
        return backStack
    }

    @Test
    fun `a push takes its forward transition from the screen coming in`() {
        val backStack = show(Under())

        backStack.add(Over())
        rule.waitForIdle()

        assertEquals(setOf("over:forward"), fired.toSet())
    }

    @Test
    fun `a pop takes its transition from the screen going out`() {
        val backStack = show(Under(), Over())

        backStack.removeLastOrNull()
        rule.waitForIdle()

        assertEquals(setOf("over:pop"), fired.toSet())
    }

    @Test
    fun `a predictive pop takes its transition from the screen being dragged away`() {
        val backStack = show(Under(), Over())

        input.backStarted(NavigationEvent(touchX = 0f, touchY = 0f, progress = 0f, swipeEdge = NavigationEvent.EDGE_LEFT))
        input.backProgressed(NavigationEvent(touchX = 10f, touchY = 0f, progress = 0.3f, swipeEdge = NavigationEvent.EDGE_LEFT))
        rule.waitForIdle()

        assertEquals(setOf("over:predictive"), fired.toSet())
        input.backCompleted()
        rule.waitForIdle()
        assertEquals(1, backStack.size)
    }
}
