package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_OVERVIEW =
    "A hacker learns from mysterious rebels about the true nature of his reality and his role in the war against its controllers. " +
        "He is pulled out of a life he took for granted and into a fight that was always his, and he has to decide what he believes."

/**
 * The film page's skeleton holds the hero, the stat row, the action row and the overview where the loaded page puts them, and at the
 * size it gives them (#2235). The page is in its common shape: a title with a long overview and something left to request, since the
 * skeleton stands in for the primary button the page leads with. Sizes are checked, so the graphics mode is native: legacy mode
 * measures text at one pixel per character.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitlePageSkeletonGeometryTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val anchors =
        listOf(LayoutAnchors.Detail.HERO, LayoutAnchors.Detail.STATS, LayoutAnchors.Detail.ACTIONS, LayoutAnchors.Detail.OVERVIEW)
            .map(LayoutAnchors::section)

    @Test
    fun `the film skeleton holds the page's first anchors`() = checkFilm()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 1.3f)
    fun `the film skeleton holds them at a font scale of 1 point 3`() = checkFilm()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2f)
    fun `the film skeleton holds them at a font scale of 2`() = checkFilm()

    /** At the expanded width the header carries the stats and the synopsis, so only the hero and the action row are the page's. */
    @Test
    @Config(qualifiers = "w840dp-h891dp-xhdpi")
    fun `the film skeleton holds the hero and the actions at the expanded width`() {
        rule.assertSkeletonReservesGeometry(
            listOf(LayoutAnchors.Detail.HERO, LayoutAnchors.Detail.ACTIONS).map(LayoutAnchors::section),
            checkSize = true,
        ) { resolved ->
            SeerrTheme {
                TitlePageScreen(
                    if (resolved) TitlePageUiState.Ready(movieFixture().copy(overview = LONG_OVERVIEW)) else TitlePageUiState.Loading,
                    emptyFlow(),
                    TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
    }

    @Test
    fun `the series skeleton holds the page's first anchors`() {
        rule.assertSkeletonReservesGeometry(anchors, checkSize = true) { resolved ->
            SeerrTheme {
                SeriesPageScreen(
                    if (resolved) SeriesPageUiState.Ready(seriesFixture().copy(overview = LONG_OVERVIEW)) else SeriesPageUiState.Loading,
                    emptyFlow(),
                    TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                    SeasonActions({}, {}, { _, _ -> }),
                )
            }
        }
    }

    private fun checkFilm() {
        rule.assertSkeletonReservesGeometry(anchors, checkSize = true) { resolved ->
            SeerrTheme {
                TitlePageScreen(
                    if (resolved) TitlePageUiState.Ready(movieFixture().copy(overview = LONG_OVERVIEW)) else TitlePageUiState.Loading,
                    emptyFlow(),
                    TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
    }
}
