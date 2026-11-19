package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.layout.LayoutAnchors
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.binge.designsystem.R as DesR

/**
 * A film with no stat to show leaves one section gap between the hero and the action row, not two: the stats' margin wrapper must not
 * be drawn empty, because a spaced column still puts its gap after a child that has no height.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitlePageNoStatsGapTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    @Test
    fun `a film with no stats puts one gap between the hero and the actions`() {
        val bare = movieFixture().copy(voteAverage = null, year = null, runtimeMinutes = null, certification = null)
        rule.setContent {
            SeerrTheme {
                TitlePageScreen(TitlePageUiState.Ready(bare), emptyFlow(), TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        val hero = rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.HERO), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val actions =
            rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.ACTIONS), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val gap = resources.getDimension(DesR.dimen.padding_m) / resources.displayMetrics.density
        assertEquals("the gap above the actions, in dp", gap, (actions.top - hero.bottom).value, 0.5f)
    }
}
