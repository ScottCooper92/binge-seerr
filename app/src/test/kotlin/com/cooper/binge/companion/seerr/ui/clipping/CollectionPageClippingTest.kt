package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.ui.discover.collection.CollectionPageActions
import com.cooper.binge.companion.seerr.ui.discover.collection.CollectionPageScreen
import com.cooper.binge.companion.seerr.ui.discover.collection.CollectionPageUiState
import com.cooper.binge.companion.seerr.ui.discover.collection.collectionFixture
import com.cooper.binge.companion.seerr.ui.discover.collection.partFixture
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val REPEATS = 5

private val STATUSES =
    listOf(
        TitleAvailability.Available,
        TitleAvailability.PartiallyAvailable,
        TitleAvailability.Processing,
        TitleAvailability.Pending,
        TitleAvailability.None,
    )

private fun worstCase(): CollectionPageUiState.Ready {
    val parts =
        STATUSES.mapIndexed { index, status ->
            partFixture(index + 1, status).copy(
                name = "The Extraordinarily Long Title Of Film Number ${index + 1} And Its Considerable Subtitle",
                overview = "A synopsis that runs on for a good while. ".repeat(REPEATS),
            )
        }
    val collection: CollectionDetails =
        collectionFixture(parts).copy(
            name = "The Extended Special Collector's Edition Trilogy Of Considerable Length",
            overview = "A saga told over many films. ".repeat(REPEATS),
        )
    return CollectionPageUiState.Ready(collection)
}

private val NO_ACTIONS = CollectionPageActions({}, {}, {}, {}, {}, {}, {}, {}, {})

/** The collection page with a film in every status and the longest names, at font scales 1.3 and 2.0, through the design system's clipping check. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionPageClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun page(fontScale: Float) = rule.assertNoClippingAt(fontScale) { CollectionPageScreen(worstCase(), emptyFlow(), NO_ACTIONS) }

    @Test fun `the collection page at 1_3`() = page(FONT_SCALE_13)

    @Test fun `the collection page at 2_0`() = page(FONT_SCALE_20)
}

/** The same page in Spanish. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionPageSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun page(fontScale: Float) = rule.assertNoClippingAt(fontScale) { CollectionPageScreen(worstCase(), emptyFlow(), NO_ACTIONS) }

    @Test fun `the collection page at 1_3 in Spanish`() = page(FONT_SCALE_13)

    @Test fun `the collection page at 2_0 in Spanish`() = page(FONT_SCALE_20)
}
