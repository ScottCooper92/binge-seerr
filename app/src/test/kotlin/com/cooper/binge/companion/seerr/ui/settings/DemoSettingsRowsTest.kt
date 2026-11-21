package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.demo.DEMO_ORIGIN
import com.cooper.binge.companion.seerr.demo.DemoSheetState
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The two rows only the demo has in Settings: "View as" and the list of request sheet states. */
@RunWith(RobolectricTestRunner::class)
class DemoSettingsRowsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var viewedAs: Boolean? = null
    private var opened: Pair<TitleKind, Int>? = null

    private fun show(
        baseUrl: String,
        viewer: Boolean = false,
    ) = rule.setContent {
        BingeExpressiveTheme(dynamicColor = false) {
            SettingsScreen(
                state =
                    SettingsUiState.Ready(
                        connection = ConnectionSummary(baseUrl, SignInKind.Session, "Alex", demoViewer = viewer),
                        server =
                            ServerSummary(
                                title = "Demo",
                                variant = SeerrVariant.Seerr,
                                versionLabel = "3.2.0",
                                updateAvailable = false,
                                commitsBehind = 0,
                            ),
                        config = null,
                    ),
                actions =
                    SettingsActions(
                        onBack = {},
                        onEditConnection = {},
                        onOpenPage = {},
                        onDisconnect = {},
                        onViewDemoAs = { viewedAs = it },
                        onOpenTitle = { kind, tmdbId -> opened = kind to tmdbId },
                    ),
            )
        }
    }

    private fun kindOf(state: DemoSheetState) = if (state.isMovie) TitleKind.Movie else TitleKind.Series

    @Test
    fun `the demo has both rows`() {
        show(DEMO_ORIGIN)
        rule.onNodeWithText("View as").assertExists()
        rule.onNodeWithText("Sheet states").assertExists()
    }

    @Test
    fun `a real server shows neither row`() {
        show("https://seerr.example.com")

        rule.onNodeWithText("View as").assertDoesNotExist()
        rule.onNodeWithText("Sheet states").assertDoesNotExist()
    }

    @Test
    fun `View as reads the role and a tap asks for the other one`() {
        show(DEMO_ORIGIN, viewer = false)
        rule.onNodeWithText("Admin").assertExists()

        rule.onNodeWithText("View as").performScrollTo().performClick()

        assertEquals(true, viewedAs)
    }

    @Test
    fun `viewing as a viewer a tap goes back to the administrator`() {
        show(DEMO_ORIGIN, viewer = true)
        rule.onNodeWithText("Viewer").assertExists()

        rule.onNodeWithText("View as").performScrollTo().performClick()

        assertEquals(false, viewedAs)
    }

    @Test
    fun `a row of the sheet states opens its title as a film or a series`() {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                DemoSheetStatesContent(onOpenTitle = { state -> opened = kindOf(state) to state.tmdbId })
            }
        }
        assertNull(opened)

        rule.onNodeWithText(DemoSheetState.PendingOthers.title).performScrollTo().performClick()
        assertEquals(TitleKind.Movie to DemoSheetState.PendingOthers.tmdbId, opened)

        rule.onNodeWithText(DemoSheetState.SeveralRequests.title).performScrollTo().performClick()
        assertEquals(TitleKind.Series to DemoSheetState.SeveralRequests.tmdbId, opened)
    }
}
