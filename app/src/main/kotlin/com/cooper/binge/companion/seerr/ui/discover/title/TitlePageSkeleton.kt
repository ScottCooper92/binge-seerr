package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.binge.designsystem.component.DetailHero
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.modifier.skeleton
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.R as DesR

private const val PLACEHOLDER_TITLE = "A Long Title On Two Lines"
private const val PLACEHOLDER_TAGLINE = "A tagline that runs one line, no more"
private val PLACEHOLDER_GENRES = listOf("Action", "Adventure", "Science Fiction")
private const val OVERVIEW_LINE_COUNT = 3
private const val OVERVIEW_LAST_LINE_FRACTION = 0.6f

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
