package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With discovery on the Settings tab's small bar and its search field stay where they are while the page scrolls under them, as the Search tab's do. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h480dp-xhdpi")
class HubPinnedBarTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private companion object {
        const val SWIPES = 6
    }

    @Test
    fun `the bar's title and the field do not move when the page scrolls to its end`() {
        assertBarAndFieldStayPut(discovery = true)
    }

    @Test
    fun `they do not with discovery off either`() {
        assertBarAndFieldStayPut(discovery = false)
    }

    private fun assertBarAndFieldStayPut(discovery: Boolean) {
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalDiscoveryEnabled provides discovery) {
                    HubScreen(
                        readyHub(HubOverview(loaded = true)),
                        HubActions(
                            onOpenSection = {},
                            onOpenAccount = {},
                            onOpenRequest = {},
                            onRetry = {},
                            onReconnect = {},
                            onDisconnect = {},
                            onDismissBingeHint = {},
                            onRefresh = {},
                        ),
                    )
                }
            }
        }
        rule.waitForIdle()
        val before = nameTops().min()
        val fieldBefore = entry().getBoundsInRoot()
        val manageBefore = manage().getBoundsInRoot().top

        repeat(SWIPES) { rule.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.7f), Offset(centerX, 0f)) } }
        rule.waitForIdle()

        assertTrue("A node reading the server's name is still at $before, where the bar's title was", before in nameTops())
        assertTrue("The page did scroll", manage().getBoundsInRoot().top < manageBefore)
        entry().assertIsDisplayed()
        assertEquals("The field kept its place", fieldBefore, entry().getBoundsInRoot())
    }

    /** Where every node reading the server's name is: the bar's title is the topmost, and the rest are rows of the page, which scroll. */
    private fun nameTops() = rule.onAllNodesWithText("Seerr").fetchSemanticsNodes().map { it.boundsInRoot.top }

    private fun manage() = rule.onNodeWithText(string(R.string.hub_manage), useUnmergedTree = true)

    private fun entry() = rule.onNode(hasText("Search", substring = true) and !hasClickAction(), useUnmergedTree = true)
}
