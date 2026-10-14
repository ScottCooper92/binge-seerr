package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val BAR_CLEARANCE = 120.dp
private const val OVERLAY = "overlay"

/** The shell's two tabs today, the start tab first. */
private val BothTabs = listOf(TvDestination.Hub, TvDestination.Issues)

/**
 * The top bar under a real D-pad: where focus starts, that Up reaches the current tab, that choosing a tab leaves focus on
 * it, how Back walks from a page to the bar and from Issues to Home, that a sheet hands focus back to what opened it, and
 * that the bar takes no focus until the server has said which tabs this account gets. Every destination is a focusable
 * box below the bar, the one property of a board the shell reacts to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvHomeShellFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var chosenTab by mutableStateOf(TvDestination.Hub)
    private var tabs by mutableStateOf(BothTabs)
    private val pages = TvOpenPages()
    private val tab get() = chosenTab.takeIf { it in tabs } ?: tabs.first()
    private var sheet by mutableStateOf<TvDestination?>(null)
    private var overlayShown by mutableStateOf(false)
    private val selections = mutableListOf<TvDestination>()

    @Test
    fun focusStartsInHomeNotTheBar() {
        setShell()

        content(TvDestination.Hub).assertIsFocused()
    }

    @Test
    fun upFromHomeLandsOnTheCurrentTab() {
        setShell()

        press(Key.DirectionUp)

        item(R.string.tv_rail_hub).assertIsFocused()
    }

    @Test
    fun choosingATabShowsItsPageAndLeavesFocusOnTheTab() {
        setShell()
        press(Key.DirectionUp)
        press(Key.DirectionRight)

        press(Key.Enter)

        assertEquals(TvDestination.Issues, tab)
        item(R.string.hub_section_issues).assertIsFocused()
        content(TvDestination.Issues).assertIsNotFocused()
    }

    @Test
    fun downFromATabEntersItsPage() {
        setShell()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.Enter)

        press(Key.DirectionDown)

        content(TvDestination.Issues).assertIsFocused()
    }

    @Test
    fun backOnAPageMovesFocusToTheCurrentTab() {
        setShell()

        pressBack()

        item(R.string.tv_rail_hub).assertIsFocused()
        assertEquals(TvDestination.Hub, tab)
    }

    @Test
    fun backOnTheBarLeavesIssuesForHome() {
        setShell()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.Enter)

        pressBack()

        assertEquals(TvDestination.Hub, tab)
        item(R.string.tv_rail_hub).assertIsFocused()
    }

    @Test
    fun choosingTheCurrentTabAgainIsReportedAsAReselect() {
        setShell(overlay = true)
        press(Key.DirectionUp)
        item(R.string.tv_rail_hub).assertIsFocused()

        press(Key.Enter)

        assertEquals(listOf(TvDestination.Hub), selections)
    }

    @Test
    fun upOutOfAnOverlayLandsOnTheCurrentTab() {
        setShell(overlay = true)
        composeTestRule.onNodeWithTag(OVERLAY).assertIsFocused()

        press(Key.DirectionUp)

        item(R.string.tv_rail_hub).assertIsFocused()
    }

    @Test
    fun aSheetOpensFromTheGearAndBackReturnsFocusToIt() {
        setShell()
        press(Key.DirectionUp)
        repeat(TvDestination.entries.size) { press(Key.DirectionRight) }
        item(R.string.hub_section_settings).assertIsFocused()

        press(Key.Enter)
        content(TvDestination.Settings).assertIsFocused()
        pressBack()

        assertNull(sheet)
        item(R.string.hub_section_settings).assertIsFocused()
    }

    @Test
    fun theBarTakesNoFocusUntilTheTabsResolve() {
        setShell(tabsResolved = false)

        press(Key.DirectionUp)

        content(TvDestination.Hub).assertIsFocused()
        item(R.string.tv_rail_hub).assertIsNotFocused()
    }

    /** With the bar hidden Back cannot move focus anywhere, so it is left to the system and the app can close. */
    @Test
    fun backIsNotHandledWhileTheBarIsHidden() {
        setShell(tabsResolved = false)

        val handled = composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() }

        assertEquals(false, handled)
    }

    /** With one tab offered there is nothing to choose between, so the bar shows no tabs at all, only the avatar and the gear. */
    @Test
    fun withOneTabTheBarShowsNoTabs() {
        setShell(offered = listOf(BothTabs.first()))

        BothTabs.forEach { composeTestRule.onAllNodes(hasContentDescription(string(it.labelRes))).assertCountEquals(0) }
        item(R.string.tv_rail_account).assertExists()
        item(R.string.hub_section_settings).assertExists()
    }

    @Test
    fun withOneTabBackOnAPageMovesFocusToTheAvatar() {
        setShell(offered = listOf(BothTabs.first()))

        pressBack()

        item(R.string.tv_rail_account).assertIsFocused()
    }

    @Test
    fun withOneTabUpFromThePageLandsOnTheAvatar() {
        setShell(offered = listOf(BothTabs.first()))

        press(Key.DirectionUp)

        item(R.string.tv_rail_account).assertIsFocused()
    }

    /** On the avatar of a one-tab bar the user is already on the start tab, so Back leaves the app. */
    @Test
    fun withOneTabBackOnTheBarIsNotHandled() {
        setShell(offered = listOf(BothTabs.first()))
        pressBack()
        item(R.string.tv_rail_account).assertIsFocused()

        val handled = composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() }

        assertEquals(false, handled)
    }

    /**
     * A tab withdrawn while it shows (a permission change, a reconnect as someone else) hands over to the start tab, and focus
     * lands on its page rather than staying with the page that went.
     */
    @Test
    fun aWithdrawnTabHandsOverToTheStartTabWithFocusOnItsPage() {
        val start = BothTabs.first()
        val other = BothTabs.last()
        setShell()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.Enter)
        press(Key.DirectionDown)
        content(other).assertIsFocused()

        tabs = listOf(start)
        composeTestRule.waitForIdle()
        composeTestRule.settleShellHandOff()

        assertEquals(start, chosenTab)
        content(other).assertDoesNotExist()
        content(start).assertIsFocused()
    }

    private fun setShell(
        offered: List<TvDestination> = BothTabs,
        tabsResolved: Boolean = true,
        overlay: Boolean = false,
    ) {
        overlayShown = overlay
        tabs = offered
        composeTestRule.setContent {
            // The connected shell's reconcile, as TvConnectedShell runs it over the offered tabs.
            LaunchedEffect(tabs) { chosenTab = pages.reconcile(chosenTab, tabs) }
            BingeTvTheme {
                TvHomeShell(
                    tab = tab,
                    sheet = sheet,
                    onSelectTab = { selected ->
                        if (selected == tab) selections += selected else chosenTab = selected
                    },
                    onOpenSheet = { sheet = it },
                    onCloseSheet = { sheet = null },
                    tabs = tabs,
                    tabsResolved = tabsResolved,
                    overlay = if (overlayShown) ({ Claiming(OVERLAY) }) else null,
                ) { destination, _ ->
                    // A tab's page leaves focus on its tab, as the boards do under LocalTvTabHosted.
                    Claiming(tag(destination), claims = destination != TvDestination.Issues)
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** A board hosted as an overlay claims first focus itself; this stand-in does the same when it [claims]. */
    @Composable
    private fun Claiming(
        tag: String,
        claims: Boolean = true,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(top = BAR_CLEARANCE)) {
            val claim = remember { FocusRequester() }
            LaunchedEffect(Unit) { if (claims) claim.requestFocus() }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag(tag)
                        .focusRequester(claim)
                        .focusable(),
            )
        }
    }

    private fun tag(destination: TvDestination) = "content-${destination.key}"

    private fun content(destination: TvDestination) = composeTestRule.onNodeWithTag(tag(destination))

    private fun item(labelRes: Int) = composeTestRule.onNode(hasContentDescription(string(labelRes)))

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }
}
