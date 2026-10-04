package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.binge.designsystem.layout.LayoutAnchors
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The resolved issue page carries the anchor [IssueDetailSkeleton] declares, `OVERVIEW` on the
 * problem report, so the geometry check can compare the two (#572). An anchor missing here would
 * read as "never compared" rather than as a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class IssueDetailAnchorsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the resolved page tags the report the skeleton reserves`() {
        rule.setContent {
            SeerrTheme {
                IssueDetailScreen(
                    state = IssueDetailUiState.Ready(detail = commonIssueDetail()),
                    events = emptyFlow(),
                    actions = noIssueDetailActions(),
                )
            }
        }

        rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW)).assertIsDisplayed()
    }
}
