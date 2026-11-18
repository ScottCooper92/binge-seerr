package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.modifier.skeleton
import com.binge.designsystem.template.HeroDetailScreen
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.ui.discover.title.OverviewSkeleton
import com.cooper.binge.companion.seerr.ui.discover.title.TitleHeroSkeleton
import com.binge.designsystem.R as DesR

private const val PART_ROW_COUNT = 3
private const val PLACEHOLDER_RATING = 7.5

private const val PLACEHOLDER_OVERVIEW =
    "A synopsis long enough to fill every line the row gives it, as most films' are. It runs on through the setting and the cast " +
        "and the one thing the whole story turns on, and it never quite ends where the row stops showing it."

/** What each placeholder row lays out: a name, a rating, a year and a full synopsis, the shape of a typical film. The shimmer hides it. */
private val PLACEHOLDER_PART =
    TitleResult(TitleKind.Movie, 0, "Film", "2000", null, null, PLACEHOLDER_OVERVIEW, PLACEHOLDER_RATING, TitleAvailability.None, null)

/**
 * Loading placeholder for a collection's page, on the same [HeroDetailScreen] frame as the page, so the bar with its Back control is
 * there from the first frame and the hero, the story and the first film's row land where the page puts them (#2235). The rows are the
 * page's own, laid out on a placeholder film under the shimmer, so each is the height the page's is at any font size. The story is
 * reserved as the page draws it when the collection has one; a collection without one grows up by that much on resolve.
 */
@Composable
internal fun CollectionPageSkeleton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroDetailScreen(
        title = "",
        onBack = onBack,
        modifier = modifier,
        contentMaxWidth = Dp.Infinity,
        darkStatusBar = false,
        hero = { TitleHeroSkeleton(cinematic = false) },
    ) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        OverviewSkeleton(Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW)))
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        Column(
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            repeat(PART_ROW_COUNT) { index ->
                val anchor = if (index == 0) Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)) else Modifier
                CollectionPartRow(
                    PLACEHOLDER_PART,
                    onClick = {},
                    modifier = anchor.clearAndSetSemantics {}.skeleton(visible = true, shape = BingeShapes.Large),
                )
            }
        }
    }
}
