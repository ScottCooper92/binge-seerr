package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.binge.designsystem.component.LocalTabReselectSignal
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.ui.hub.HubActions
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import com.cooper.binge.companion.seerr.ui.hub.HubScreen
import com.cooper.binge.companion.seerr.ui.hub.HubServer
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubUserLoad
import com.cooper.binge.companion.seerr.ui.hubs.DiscoverHubScreen
import com.cooper.binge.companion.seerr.ui.hubs.DiscoverHubUiState
import com.cooper.binge.companion.seerr.ui.hubs.HubRowUi
import com.cooper.binge.companion.seerr.ui.search.title.TitleSearchActions
import com.cooper.binge.companion.seerr.ui.search.title.TitleSearchScreen
import com.cooper.binge.companion.seerr.ui.search.title.TitleSearchUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What tapping the tab that is already selected does to the screen at its root (#2356). The shell pops what the tab opened and emits the
 * tab's key on [LocalTabReselectSignal], which `design-system` tests on its own; what is here is that each of this app's roots listens
 * for its own tab and does the right thing with it. The signal is driven directly, as the shell would.
 */
@RunWith(RobolectricTestRunner::class)
class TabReselectTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val signal = MutableSharedFlow<Any>(extraBufferCapacity = 1)

    private fun reselect(tab: SeerrTab) {
        runBlocking { signal.emit(tab) }
        rule.waitForIdle()
    }

    @Test
    fun `reselecting search focuses its field and keeps the query`() {
        rule.setContent {
            CompositionLocalProvider(LocalTabReselectSignal provides signal) {
                SeerrTheme {
                    TitleSearchScreen(state = TitleSearchUiState.Idle("harbour", emptyList()), actions = noActions())
                }
            }
        }
        rule.onNode(hasSetTextAction()).assertIsNotFocused()

        reselect(SeerrTab.Search)

        rule.onNode(hasSetTextAction()).assertIsFocused()
        rule.onNodeWithText("harbour").assertIsDisplayed()
    }

    @Test
    fun `another tab's reselect leaves the search field alone`() {
        rule.setContent {
            CompositionLocalProvider(LocalTabReselectSignal provides signal) {
                SeerrTheme {
                    TitleSearchScreen(state = TitleSearchUiState.Idle("harbour", emptyList()), actions = noActions())
                }
            }
        }

        reselect(SeerrTab.Movies)

        rule.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun `reselecting a discovery hub scrolls it back to the top`() {
        rule.setContent {
            CompositionLocalProvider(LocalTabReselectSignal provides signal) {
                SeerrTheme {
                    DiscoverHubScreen(
                        tab = HubTab.Movies,
                        state = manyRows(),
                        onRefresh = {},
                        onOpenTitle = {},
                        onOpenGrid = { _, _ -> },
                        onOpenRequests = {},
                    )
                }
            }
        }
        rule.onNodeWithText("Row 0").assertIsDisplayed()
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(ROWS - 1)
        rule.waitForIdle()
        rule.onNodeWithText("Row 0").assertDoesNotExist()

        reselect(SeerrTab.Movies)
        rule.waitUntil(WAIT_MILLIS) { rule.onAllNodesWithText("Row 0").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("Row 0").assertIsDisplayed()
    }

    @Test
    fun `reselecting settings scrolls its hub back to the top`() {
        rule.setContent {
            CompositionLocalProvider(LocalTabReselectSignal provides signal) {
                SeerrTheme { HubScreen(state = hubReady(), actions = hubActions()) }
            }
        }
        rule.onNode(hasScrollAction()).performTouchInput {
            swipeUp()
            swipeUp()
        }
        rule.waitForIdle()
        assertTrue("the dashboard did not scroll, so the test proves nothing", scrolledBy() > 0)

        reselect(SeerrTab.Settings)
        rule.waitUntil(WAIT_MILLIS) { scrolledBy() == 0 }
    }

    @Test
    fun `another tab's reselect leaves the settings hub where it is`() {
        rule.setContent {
            CompositionLocalProvider(LocalTabReselectSignal provides signal) {
                SeerrTheme { HubScreen(state = hubReady(), actions = hubActions()) }
            }
        }
        rule.onNode(hasScrollAction()).performTouchInput {
            swipeUp()
            swipeUp()
        }
        rule.waitForIdle()
        val before = scrolledBy()
        assertTrue("the dashboard did not scroll, so the test proves nothing", before > 0)

        reselect(SeerrTab.Search)

        assertTrue("a reselect of another tab moved the hub", scrolledBy() == before)
    }

    @Test
    fun `the series hub listens for its own tab and not the movies one`() {
        rule.setContent {
            CompositionLocalProvider(LocalTabReselectSignal provides signal) {
                SeerrTheme {
                    DiscoverHubScreen(
                        tab = HubTab.Series,
                        state = manyRows(HubTab.Series),
                        onRefresh = {},
                        onOpenTitle = {},
                        onOpenGrid = { _, _ -> },
                        onOpenRequests = {},
                    )
                }
            }
        }
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(ROWS - 1)
        rule.waitForIdle()

        reselect(SeerrTab.Movies)
        rule.onNodeWithText("Row 0").assertDoesNotExist()

        reselect(SeerrTab.Tv)

        rule.waitUntil(WAIT_MILLIS) { rule.onAllNodesWithText("Row 0").fetchSemanticsNodes().isNotEmpty() }
    }

    /** How far the first scrolling node has scrolled, in pixels. */
    private fun scrolledBy(): Int =
        rule
            .onAllNodes(hasScrollAction())
            .onFirst()
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?.value
            ?.invoke()
            ?.toInt() ?: 0

    private fun hubActions() =
        HubActions(
            onOpenSection = {},
            onOpenAccount = {},
            onOpenRequest = {},
            onRetry = {},
            onReconnect = {},
            onDisconnect = {},
            onDismissBingeHint = {},
            onRefresh = {},
        )

    private fun hubReady() =
        HubUiState.Ready(
            server = HubServer("https://seerr.example.org", "Front Room Seerr", SeerrVariant.Seerr, "3.1.0", false, 0),
            health = ConnectionHealth.Healthy,
            overview =
                HubOverview(
                    loaded = true,
                    userLoad = HubUserLoad.Loaded,
                    account = HubAccount(id = 1, name = "Ada", isAdmin = true, avatarUrl = null),
                    permissions = SeerrPermissions(isAdmin = true),
                    movieRequestCount = 128,
                    tvRequestCount = 47,
                    pendingRequestCount = 6,
                    openIssueCount = 2,
                    userCount = 9,
                    blocklistCount = 3,
                    hasIssues = true,
                    hasBlocklist = true,
                ),
            downloading = emptyList(),
            bingeStatus = BingeStatus.Hidden,
        )

    private fun noActions() = TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})

    private fun manyRows(tab: HubTab = HubTab.Movies) =
        DiscoverHubUiState.Ready(
            rows = (0 until ROWS).map { HubRowUi.Cards(HubRow.Popular(it, "Row $it", tab), cards(it)) },
            pending = 0,
            refreshing = false,
            heroPlace = false,
        )

    private fun cards(row: Int) =
        (0 until 3).map {
            HubCard(TitleKind.Movie, row * 10 + it, "Title $row-$it", "2020", null, TitleAvailability.None, "https://server.example/m/$it")
        }

    private companion object {
        const val ROWS = 12
        const val WAIT_MILLIS = 5_000L
    }
}
