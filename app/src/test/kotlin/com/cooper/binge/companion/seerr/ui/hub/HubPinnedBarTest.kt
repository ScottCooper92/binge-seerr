package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With discovery on the Settings tab's small bar stays where it is while the page scrolls under it, as the Search tab's does. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h480dp-xhdpi")
class HubPinnedBarTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the bar's title does not move when the page scrolls`() {
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalDiscoveryEnabled provides true) {
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
        val entryBefore = entry().getBoundsInRoot().top

        repeat(2) { rule.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.7f), Offset(centerX, 0f)) } }
        rule.waitForIdle()

        assertTrue("A node reading the server's name is still at $before, where the bar's title was", before in nameTops())
        assertTrue("The page did scroll", entry().getBoundsInRoot().top < entryBefore)
    }

    /** Where every node reading the server's name is: the bar's title is the topmost, and the rest are rows of the page, which scroll. */
    private fun nameTops() = rule.onAllNodesWithText("Seerr").fetchSemanticsNodes().map { it.boundsInRoot.top }

    private fun entry() = rule.onNode(hasText("Search", substring = true) and !hasClickAction(), useUnmergedTree = true)
}
