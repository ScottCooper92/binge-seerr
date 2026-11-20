package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.CarouselSkeleton
import com.binge.designsystem.component.DetailHero
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.DetailStatRow
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.component.statsPerRow
import com.binge.designsystem.isExpandedLayout
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.modifier.skeleton
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.template.HeroDetailScreen
import com.binge.designsystem.template.readingMargin
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.R as DesR

private const val PLACEHOLDER_TITLE = "A Long Title On Two Lines"
private const val PLACEHOLDER_TAGLINE = "A tagline that runs one line, no more"
private val PLACEHOLDER_GENRES = listOf("Action", "Adventure", "Science Fiction")
private const val STAT_COUNT = 3
private const val OVERVIEW_LINE_COUNT = 3
private const val OVERVIEW_LAST_LINE_FRACTION = 0.6f

/**
 * Loading placeholder for a film's or a series' page, on the same [HeroDetailScreen] frame as the page, so the bar with its Back
 * control is there from the first frame and the hero, stats row, action row and overview land where the page puts them (#2235).
 * The hero's text and every section below the overview depend on what the server returns, so only the first anchors are held to
 * the page: the rest grows on resolve. At the expanded width the header carries the stats and the synopsis, as the page's does.
 */
@Composable
internal fun TitlePageSkeleton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cinematic = isExpandedLayout()
    HeroDetailScreen(
        title = "",
        onBack = onBack,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        heroHeight = dimensionResource(if (cinematic) DesR.dimen.detail_cinematic_header_height else DesR.dimen.detail_hero_height),
        horizontalInset = dimensionResource(if (cinematic) DesR.dimen.detail_cinematic_header_padding else DesR.dimen.padding_m),
        contentMaxWidth = Dp.Infinity,
        darkStatusBar = false,
        hero = { TitleHeroSkeleton(cinematic) },
    ) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
            if (!cinematic) StatsRowsSkeleton(Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.STATS)))
            ActionsSkeleton(Modifier.readingMargin().layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.ACTIONS)))
            if (!cinematic) OverviewSkeleton(Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW)).readingMargin())
            CarouselSkeleton()
        }
    }
}

/**
 * The hero: the page's own [DetailHero] laid out on placeholder text under one plain shimmer, so its height is the loaded hero's, which
 * grows with the title, the genres and the tagline, and no placeholder image is drawn in it. The placeholder is a typical film's:
 * a title over two lines, three genres and a tagline of one. Its semantics are cleared, since the text is never shown or read out.
 * At the expanded width the cinematic header has a fixed height, so a plate of that height stands in for it.
 */
@Composable
internal fun TitleHeroSkeleton(
    cinematic: Boolean,
    modifier: Modifier = Modifier,
    title: String = PLACEHOLDER_TITLE,
    tagline: String? = PLACEHOLDER_TAGLINE,
    genres: List<String> = PLACEHOLDER_GENRES,
    metaText: String = "",
) {
    val anchor = modifier.fillMaxWidth().layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.HERO))
    if (cinematic) {
        Box(anchor.height(dimensionResource(DesR.dimen.detail_cinematic_header_height)).skeleton(visible = true, shape = RectangleShape))
    } else {
        Box(anchor.clearAndSetSemantics {}.skeleton(visible = true, shape = RectangleShape)) {
            DetailHero(
                title = title,
                backdropUrl = null,
                tagline = tagline,
                metaText = metaText,
                onBack = {},
                genres = genres,
                showChrome = false,
                richBackdrop = true,
            )
        }
    }
}

/**
 * The stat rows: [STAT_COUNT] blank stats in the page's own [DetailStatRow], laid out by its rule, so a large font breaks them into
 * the rows the page does and each is the height of the page's. The shimmer covers the row.
 */
@Composable
private fun StatsRowsSkeleton(modifier: Modifier = Modifier) {
    val blank = List(STAT_COUNT) { DetailStat(Icons.Filled.Star, " ", " ") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        blank.chunked(statsPerRow()).forEach { row ->
            Box(Modifier.readingMargin().fillMaxWidth().padding(resolvedContentPadding())) {
                DetailStatRow(row, Modifier.skeleton(visible = true, shape = BingeShapes.Large), contentPadding = PaddingValues())
            }
        }
    }
}

/** The action row: the primary button's plate, which is the loaded page's Request or its status chip. */
@Composable
private fun ActionsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m))) {
        SkeletonPlate(Modifier.fillMaxWidth().height(dimensionResource(DesR.dimen.button_filled_height)))
    }
}

/**
 * The "Overview" heading, the collapsed synopsis and the expand toggle's row. Each line is a blank [Text] in the page's own style under
 * a plate, so its height follows the font as the page's does, and the toggle is the touch target the page's button is. [modifier] sits
 * outside the page's inset, as the design system's section does; [insideInset] sits inside it, for a page that anchors there, and
 * [inset] is the page's own, where it keeps to a flat one.
 */
@Composable
internal fun OverviewSkeleton(
    modifier: Modifier = Modifier,
    insideInset: Modifier = Modifier,
    inset: Dp = resolvedContentInset(),
) {
    Column(modifier = modifier.padding(horizontal = inset).then(insideInset)) {
        TextPlate(MaterialTheme.typography.titleMedium, Modifier.width(dimensionResource(DesR.dimen.skeleton_header_width)))
        Spacer(Modifier.height(dimensionResource(DesR.dimen.detail_meta_spacing)))
        repeat(OVERVIEW_LINE_COUNT) { index ->
            TextPlate(
                MaterialTheme.typography.bodyMedium,
                Modifier.fillMaxWidth(if (index == OVERVIEW_LINE_COUNT - 1) OVERVIEW_LAST_LINE_FRACTION else 1f),
            )
        }
        Box(Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.CenterStart) {
            TextPlate(MaterialTheme.typography.labelLarge, Modifier.width(dimensionResource(DesR.dimen.skeleton_header_width)))
        }
    }
}

/**
 * A plate one line of [style] tall: blank text sets the height, so it is the line's at any font size, and the shimmer is inset from
 * the top and bottom of it, so lines stacked one on the next read as separate bars.
 */
@Composable
private fun TextPlate(
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Text(text = " ", style = style, modifier = Modifier.alpha(0f))
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing))
                .skeleton(visible = true, shape = BingeShapes.Tag),
        )
    }
}
