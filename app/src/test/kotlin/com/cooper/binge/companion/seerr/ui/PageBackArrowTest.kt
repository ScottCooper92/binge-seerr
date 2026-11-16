package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.settings.licences.LicencesScreen
import com.cooper.binge.companion.seerr.ui.settings.licences.LicencesUiState
import com.cooper.binge.companion.seerr.ui.users.settings.EditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.EditorPage
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.binge.designsystem.R as DesR

/**
 * A page the settings search opens straight beside the hub is at depth 1, where the hub is the way back, so it draws no arrow
 * (#1998); stacked deeper, or alone in the window, it keeps it. Two screens stand for the two shapes of page: the shared editor
 * frame, which most pages use, and a screen that builds its own scaffold.
 */
@RunWith(RobolectricTestRunner::class)
class PageBackArrowTest {
    @get:Rule
    val compose: ComposeContentTestRule = createSeerrComposeRule()

    private fun arrows(
        hubBeside: Boolean,
        paneDepth: Int,
        page: @Composable () -> Unit,
    ): Int {
        compose.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalIsSinglePaneNav provides !hubBeside, LocalPaneDepth provides paneDepth) { page() }
            }
        }
        compose.waitForIdle()
        return compose.onAllNodesWithContentDescription(string(DesR.string.cd_navigate_back)).fetchSemanticsNodes().size
    }

    private val editor: @Composable () -> Unit = {
        EditorPage(
            title = "Page",
            state = EditorUiState.Loading,
            events = emptyFlow(),
            actions = EditorActions<Unit>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {}),
        ) { _, _ -> }
    }

    private val licences: @Composable () -> Unit = { LicencesScreen(LicencesUiState.Loading, onBack = {}, onOpenWebsite = {}) }

    @Test
    fun `an editor page straight beside the hub has no arrow`() = assertEquals(0, arrows(hubBeside = true, paneDepth = 1, page = editor))

    @Test
    fun `an editor page stacked beside the hub, or alone in the window, keeps its arrow`() {
        assertEquals(1, arrows(hubBeside = true, paneDepth = 2, page = editor))
    }

    @Test
    fun `a page with its own scaffold straight beside the hub has no arrow`() =
        assertEquals(0, arrows(hubBeside = true, paneDepth = 1, page = licences))

    @Test
    fun `a page with its own scaffold alone in the window keeps its arrow`() =
        assertEquals(1, arrows(hubBeside = false, paneDepth = 1, page = licences))
}
