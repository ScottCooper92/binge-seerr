package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.component.BingeNavPresentation
import com.binge.designsystem.component.BingeNavSuiteItem
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.push
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private enum class CounterTab { Alpha, Beta }

private data class CounterPage(
    val id: Int,
) : NavKey

@RunWith(RobolectricTestRunner::class)
class TabShellTest {
    @get:Rule
    val compose: ComposeContentTestRule = createSeerrComposeRule()

    private val alpha = NavBackStack<NavKey>(CounterPage(1))
    private val beta = NavBackStack<NavKey>(CounterPage(2))
    private val state = TabShellState(CounterTab.entries, mutableIntStateOf(0), mapOf(CounterTab.Alpha to alpha, CounterTab.Beta to beta))

    private fun show() {
        compose.setContent {
            SeerrTheme {
                TabShell(
                    state = state,
                    items =
                        CounterTab.entries.map {
                            BingeNavSuiteItem(
                                key = it,
                                label = it.name,
                                icon = Icons.Filled.Home,
                                testTag = "tab_$it",
                            )
                        },
                    presentation = BingeNavPresentation.BottomBar,
                ) { tab -> Counter(tab.name) }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Counter(name: String) {
        var count by rememberSaveable { mutableIntStateOf(0) }
        Text("$name $count", Modifier.clickable { count++ })
    }

    @Test
    fun `the selected tab shows and a tap on another tab swaps its content in`() {
        show()
        compose.onNodeWithText("Alpha 0").assertIsDisplayed()

        compose.onNodeWithTag("tab_Beta").performClick()

        compose.onNodeWithText("Beta 0").assertIsDisplayed()
        assertEquals(CounterTab.Beta, state.selected)
    }

    @Test
    fun `a tab comes back with the state it was left in`() {
        show()
        compose.onNodeWithText("Alpha 0").performClick()
        compose.onNodeWithText("Alpha 1").assertIsDisplayed()

        compose.onNodeWithTag("tab_Beta").performClick()
        compose.onNodeWithTag("tab_Alpha").performClick()

        compose.onNodeWithText("Alpha 1").assertIsDisplayed()
    }

    @Test
    fun `a tap on the tab that is showing returns it to its root`() {
        show()
        alpha.push(CounterPage(10))

        compose.onNodeWithTag("tab_Alpha").performClick()

        assertEquals(listOf(CounterPage(1)), alpha.toList())
        assertEquals(CounterTab.Alpha, state.selected)
    }
}
