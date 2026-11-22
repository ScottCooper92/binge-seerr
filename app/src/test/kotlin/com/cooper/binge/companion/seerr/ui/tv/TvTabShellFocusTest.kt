package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.focus.tvShellArrivalTarget
import com.binge.designsystem.tv.nav.LocalTvTopInset
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Tabs = listOf(TvTab.Search, TvTab.Movies, TvTab.Requests, TvTab.Issues)

/**
 * The design system's tab scaffold (homelab-design-system#1392) under a real D-pad, through this app's [TvTabShell]: where focus
 * starts, that Up reaches the tab, that a tab commits on click, Back from the page to the tab and from another tab to the start
 * tab, the gear's sheet handing focus back to the gear, the avatar's menu, and where the avatar sits in the walk of the bar. Every
 * page is a focusable box that claims the shell's arrival, as a board would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvTabShellFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var tab by mutableStateOf(TvTab.Requests)
    private var sheetOpen by mutableStateOf(false)
    private var topLayerOpen by mutableStateOf(false)
    private var plainPage = false
    private var emptyTabs = emptySet<TvTab>()

    private fun setShell() {
        composeTestRule.setContent {
            BingeTvTheme {
                val requester = remember { FocusRequester() }
                TvTabShell(
                    tab = tab,
                    tabs = Tabs,
                    home = TvTab.Requests,
                    onSelectTab = { tab = it },
                    sheetOpen = sheetOpen,
                    onOpenSheet = { sheetOpen = true },
                    onCloseSheet = { sheetOpen = false },
                    account = TvTopBarAccount(name = "Ada Lovelace", avatarUrl = null),
                    contentRequester = requester,
                    topLayer =
                        if (topLayerOpen) {
                            { closer ->
                                BackHandler { closer.close() }
                                Layer("top-layer")
                            }
                        } else {
                            null
                        },
                    onCloseTopLayer = { topLayerOpen = false },
                    sheet = { Layer("sheet") },
                ) { current -> if (current in emptyTabs) EmptyPlate() else Claiming("page-${current.name}") }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Composable
    private fun Claiming(tag: String) {
        val claim = remember { FocusRequester() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = LocalTvTopInset.current)
                    .testTag(tag)
                    .tvShellArrivalTarget(claim)
                    .focusable(),
        )
    }

    /** The real empty state of the Issues and Requests boards: nothing to focus, and no focus sink of its own. */
    @Composable
    private fun EmptyPlate() {
        TvRowsFallback(
            rows = listOf(TvPagedRows<Any>(count = 0, at = { null })),
            emptyBody = "Nothing here",
            onRetryLoad = {},
            modifier = Modifier.testTag("empty-plate"),
        )
    }

    /** A sheet or a title's page is no destination, so the shell owes it no arrival: it takes focus itself, as a sheet's own page does. */
    @Composable
    private fun Layer(tag: String) {
        val claim = remember { FocusRequester() }
        LaunchedEffect(Unit) { claim.requestFocus() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(tag)
                    .focusRequester(claim)
                    .focusable(),
        )
    }

    @Test
    fun focusStartsOnTheStartTabsPage() {
        setShell()

        page(TvTab.Requests).assertIsFocused()
    }

    @Test
    fun upFromThePageLandsOnTheCurrentTab() {
        setShell()

        press(Key.DirectionUp)

        item(R.string.hub_section_requests).assertIsFocused()
    }

    @Test
    fun aTabCommitsOnClickAndLeavesFocusOnItAndDownEntersItsPage() {
        setShell()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        item(R.string.hub_section_issues).assertIsFocused()
        assertEquals("walking the bar commits nothing", TvTab.Requests, tab)

        press(Key.Enter)
        assertEquals(TvTab.Issues, tab)
        item(R.string.hub_section_issues).assertIsFocused()
        page(TvTab.Issues).assertIsNotFocused()
        press(Key.DirectionDown)

        page(TvTab.Issues).assertIsFocused()
    }

    @Test
    fun backOnAPageMovesFocusToTheTabAndBackOnAnotherTabGoesToTheStartTab() {
        setShell()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.Enter)
        press(Key.DirectionDown)
        page(TvTab.Issues).assertIsFocused()

        pressBack()
        item(R.string.hub_section_issues).assertIsFocused()
        pressBack()

        assertEquals(TvTab.Requests, tab)
        item(R.string.hub_section_requests).assertIsFocused()
    }

    @Test
    fun backOnTheStartTabIsNotHandledSoTheAppCanClose() {
        setShell()
        pressBack()
        item(R.string.hub_section_requests).assertIsFocused()

        val handled = composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() }

        assertEquals(false, handled)
    }

    /** The gear is the end of the bar: Right from the last tab lands on it, and Enter opens the sheet. */
    @Test
    fun theGearOpensTheSheetAndClosingItReturnsFocusToTheGear() {
        setShell()
        press(Key.DirectionUp)
        repeat(Tabs.size) { press(Key.DirectionRight) }
        item(R.string.shell_tab_settings).assertIsFocused()

        press(Key.Enter)
        composeTestRule.onNodeWithTag("sheet").assertIsFocused()
        pressBack()
        composeTestRule.settleShellHandOff()

        assertEquals(false, sheetOpen)
        item(R.string.shell_tab_settings).assertIsFocused()
    }

    /** The scaffold puts the avatar at the START of the bar, before the tabs; Scott's decision is the avatar on the right. */
    @Test
    fun theAvatarIsAtTheStartOfTheBarBeforeTheTabs() {
        setShell()
        press(Key.DirectionUp)
        repeat(Tabs.size) { press(Key.DirectionLeft) }

        item(R.string.tv_account).assertIsFocused()
    }

    @Test
    fun theAvatarsMenuTakesTheFirstBackAndOpensNoSheet() {
        setShell()
        press(Key.DirectionUp)
        repeat(Tabs.size) { press(Key.DirectionLeft) }
        item(R.string.tv_account).assertIsFocused()

        pressBack()

        item(R.string.tv_account).assertIsFocused()
        assertEquals(false, sheetOpen)
        assertEquals(TvTab.Requests, tab)
    }

    @Test
    fun aTopLayerOverAPlainPageHandsFocusBackToIt() {
        plainPage = true
        setShell()
        topLayerOpen = true
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.settleShellHandOff()

        page(TvTab.Requests).assertIsFocused()
    }

    @Test
    fun aTopLayerTrapsFocusAndClosingItHandsFocusBackToThePage() {
        setShell()
        topLayerOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top-layer").assertIsFocused()

        press(Key.DirectionUp)
        composeTestRule.onNodeWithTag("top-layer").assertIsFocused()
        pressBack()
        composeTestRule.settleShellHandOff()
        println(
            "FOCUSED NOW: " +
                composeTestRule
                    .onAllNodes(
                        androidx.compose.ui.test
                            .isFocused(),
                    ).fetchSemanticsNodes()
                    .map { it.config.toString() },
        )

        page(TvTab.Requests).assertIsFocused()
    }

    /** Nothing under the bar can take focus, so Down from the tab does nothing: focus stays on the tab, not on something unseen. */
    @Test
    fun downFromTheIssuesTabOnAnEmptyPageKeepsFocusOnTheTab() {
        tab = TvTab.Issues
        emptyTabs = setOf(TvTab.Issues)
        setShell()
        item(R.string.hub_section_issues).requestFocus()
        item(R.string.hub_section_issues).assertIsFocused()

        press(Key.DirectionDown)

        item(R.string.hub_section_issues).assertIsFocused()
    }

    @Test
    fun downFromTheRequestsTabOnAnEmptyPageKeepsFocusOnTheTab() {
        emptyTabs = setOf(TvTab.Requests)
        setShell()
        item(R.string.hub_section_requests).requestFocus()
        item(R.string.hub_section_requests).assertIsFocused()

        press(Key.DirectionDown)

        item(R.string.hub_section_requests).assertIsFocused()
    }

    /** A title's page closing over an empty page hands focus up to the bar, which it can still move along. */
    @Test
    fun aTopLayerClosingOverAnEmptyPageLeavesFocusOnTheBar() {
        emptyTabs = setOf(TvTab.Requests)
        setShell()
        topLayerOpen = true
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.settleShellHandOff()

        item(R.string.hub_section_requests).assertIsFocused()
        press(Key.DirectionRight)
        item(R.string.hub_section_requests).assertIsNotFocused()
    }

    /**
     * The page taking focus straight back after a layer closes leaves nothing owed to the sink, so the bar's Down onto an empty
     * tab later still finds nothing to land on.
     */
    @Test
    fun aHandBackThatNeededNoSinkLeavesNoSinkForALaterEmptyTab() {
        emptyTabs = setOf(TvTab.Issues)
        setShell()
        topLayerOpen = true
        composeTestRule.waitForIdle()
        pressBack()
        composeTestRule.settleShellHandOff()
        page(TvTab.Requests).assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.Enter)
        item(R.string.hub_section_issues).assertIsFocused()

        press(Key.DirectionDown)

        item(R.string.hub_section_issues).assertIsFocused()
    }

    private fun page(tab: TvTab) = composeTestRule.onNodeWithTag("page-${tab.name}")

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
