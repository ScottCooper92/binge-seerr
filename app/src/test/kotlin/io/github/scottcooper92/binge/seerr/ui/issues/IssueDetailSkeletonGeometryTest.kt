package io.github.scottcooper92.binge.seerr.ui.issues

import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The issue skeleton reserves the report the resolved page fills (#572), swapped by the screen itself.
 * The page is in its common shape — a report, a reply and a manager with the pinned bar — so a
 * skeleton sized for a page without a report fails. The report's top is where the header and divider
 * end, so this also holds the header's height.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IssueDetailSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the skeleton holds the report where the page puts it`() {
        rule.assertSkeletonReservesGeometry(listOf(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW))) { resolved ->
            SeerrTheme {
                IssueDetailScreen(
                    state = if (resolved) IssueDetailUiState.Ready(detail = commonIssueDetail()) else IssueDetailUiState.Loading,
                    events = emptyFlow(),
                    actions = noIssueDetailActions(),
                )
            }
        }
    }
}
