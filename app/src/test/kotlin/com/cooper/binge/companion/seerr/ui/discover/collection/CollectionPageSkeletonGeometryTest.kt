package com.cooper.binge.companion.seerr.ui.discover.collection

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

private const val LONG_STORY =
    "A saga of films about a family that keeps a border town alive. It runs over many winters and every one of them ends worse than " +
        "the last, until the last film asks what the town was ever for."

/**
 * The collection skeleton holds the hero, the story and the first film's row where the loaded page puts them, and at the size it
 * gives them (#2235). The collection has a story, which is the page's common shape. Sizes are checked, so the graphics mode is native.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionPageSkeletonGeometryTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val anchors =
        listOf(LayoutAnchors.Detail.HERO, LayoutAnchors.Detail.OVERVIEW, LayoutAnchors.Collection.FIRST_ITEM).map(LayoutAnchors::section)

    @Test
    fun `the collection skeleton holds the page's first anchors`() = check()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2f)
    fun `the collection skeleton holds them at a font scale of 2`() = check()

    /** A collection in its common shape: a story, and films whose synopses run past the lines the row gives them. */
    private fun collection() = collectionFixture(List(2) { partFixture(it + 1).copy(overview = LONG_STORY) }).copy(overview = LONG_STORY)

    private fun check() {
        rule.assertSkeletonReservesGeometry(anchors, checkSize = true) { resolved ->
            SeerrTheme {
                CollectionPageScreen(
                    if (resolved) CollectionPageUiState.Ready(collection()) else CollectionPageUiState.Loading,
                    emptyFlow(),
                    CollectionPageActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
    }
}
