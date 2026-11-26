package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.binge.designsystem.component.LocalTabReselectSignal
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Settings tab's heading scrolls away as the page scrolls, as Binge's Account's does, and its search field stays pinned at the top while the page scrolls under it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h480dp-xhdpi")
class HubPinnedBarTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val signal = MutableSharedFlow<Any>(extraBufferCapacity = 1)

    private companion object {
        const val SWIPES = 6
    }

    @Test
    fun `the heading scrolls away and the field stays pinned when the page scrolls to its end`() {
        assertHeadingGoesAndFieldStaysPut(discovery = true)
    }

    @Test
    fun `the same with discovery off`() {
        assertHeadingGoesAndFieldStaysPut(discovery = false)
    }

    @Test
    fun `reselecting the Settings tab brings the heading back with the page`() {
        showHub(discovery = true)
        val heading = nameTops().min()
        scrollPage()
        assertTrue("The heading scrolled away", heading !in nameTops())

        runBlocking { signal.emit(SeerrTab.Settings) }
        rule.waitForIdle()

        assertTrue("The heading is back at $heading", heading in nameTops())
    }

    private fun showHub(discovery: Boolean) {
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalDiscoveryEnabled provides discovery, LocalTabReselectSignal provides signal) {
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
    }

    private fun assertHeadingGoesAndFieldStaysPut(discovery: Boolean) {
        showHub(discovery)
        val heading = nameTops().min()
        val fieldBefore = entry().getBoundsInRoot()
        val pageBefore = nameTops().max()

        scrollPage()
        val pinned = entry().getBoundsInRoot()
        scrollPage()

        assertTrue("The heading that was at $heading scrolled away", heading !in nameTops())
        assertTrue("The page did scroll", nameTops().isEmpty() || nameTops().max() < pageBefore)
        entry().assertIsDisplayed()
        assertTrue("The field moved up to the top as the heading left", pinned.top < fieldBefore.top)
        assertEquals("The field kept its place once pinned", pinned, entry().getBoundsInRoot())
    }

    private fun scrollPage() {
        repeat(SWIPES) { rule.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.7f), Offset(centerX, 0f)) } }
        rule.waitForIdle()
    }

    /** Where every node reading the server's name is: the heading is the topmost, and the rest are rows of the page, which scroll. */
    private fun nameTops() = rule.onAllNodesWithText("Seerr").fetchSemanticsNodes().map { it.boundsInRoot.top }

    private fun entry() = rule.onNode(hasText("Search", substring = true) and !hasClickAction(), useUnmergedTree = true)
}
