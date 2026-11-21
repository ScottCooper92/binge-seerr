package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
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
        val before = rule.onAllNodesWithText("Seerr")[0].getBoundsInRoot()
        val entryBefore = entry().getBoundsInRoot().top

        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()

        val after = rule.onAllNodesWithText("Seerr")[0].getBoundsInRoot()
        assertEquals("The title stayed put", before.top, after.top)
        assertTrue("The page did scroll", entry().getBoundsInRoot().top < entryBefore)
    }

    private fun entry() = rule.onNode(hasText("Search", substring = true) and !hasClickAction(), useUnmergedTree = true)
}
