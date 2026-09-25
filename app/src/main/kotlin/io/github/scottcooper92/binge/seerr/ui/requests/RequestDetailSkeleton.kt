package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.component.ImagePlaceholder
import com.binge.designsystem.component.lineHeightOf
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.labelSmallEmphasis
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.SkeletonPlate
import com.binge.designsystem.R as DesR

private const val HERO_TITLE_FRACTION = 0.7f
private const val HERO_META_FRACTION = 0.35f
private const val HEADLINE_CHIP_COUNT = 2
private const val OVERVIEW_LINE_COUNT = 3
private const val OVERVIEW_LAST_LINE_FRACTION = 0.6f

/** [RequestFacts]'s one unconditional row — Requested by, now a leading icon plus one line. */
private const val FACTS_VALUE_FRACTION = 0.55f

/**
 * Loading placeholder for [RequestDetailPage]: the hero, the headline's chip row and overview, and
 * the one facts row every request has. Seasons, downloads, siblings, watch data and Moderated by are
 * all conditional on what the server returns, so nothing is reserved for them — the scroll grows on
 * resolve rather than reflowing a guess, the same trade Binge's own `DetailScreenSkeleton` makes for
 * its cast rail (#373).
 *
 * The primary action footer IS reserved even though `RequestDetail.hasPrimaryAction` can be false
 * (a viewer with no manage permission on an already-settled request) — reserving the common admin
 * case over the rare read-only one, the same trade that skeleton makes for its details band.
 *
 * Insets mirror [RequestDetailPage] exactly rather than a blanket `safeDrawingPadding()`: the hero
 * runs full-bleed under the status bar on both, and only the footer clears the navigation bar —
 * matching that is what keeps the [LayoutAnchors.Detail.HERO] anchor from moving on resolve.
 */
@Composable
internal fun RequestDetailSkeleton(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                HeroSkeleton(modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.HERO)))
                HeadlineSkeleton(
                    modifier =
                        Modifier
                            .padding(resolvedContentInset())
                            .layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW)),
                )
                FactsSkeleton(modifier = Modifier.padding(horizontal = resolvedContentInset()))
            }
            PrimaryActionSkeleton()
        }
    }
}

@Composable
private fun HeroSkeleton(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(dimensionResource(DesR.dimen.detail_hero_height))) {
        ImagePlaceholder(Modifier.fillMaxSize())
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = resolvedContentInset(),
                        end = resolvedContentInset(),
                        bottom = dimensionResource(DesR.dimen.detail_hero_text_bottom_padding),
                    ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            // The title and meta line only — RequestDetailPage passes no tagline/eyebrow, so those
            // rows never render and reserving them would leave a gap the resolved hero never fills.
            SkeletonPlate(
                Modifier
                    .fillMaxWidth(HERO_TITLE_FRACTION)
                    .height(lineHeightOf(MaterialTheme.typography.displaySmall)),
            )
            SkeletonPlate(
                Modifier
                    .fillMaxWidth(HERO_META_FRACTION)
                    .height(lineHeightOf(MaterialTheme.typography.bodySmall)),
            )
        }
    }
}

@Composable
private fun HeadlineSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            repeat(HEADLINE_CHIP_COUNT) { ChipSkeleton() }
        }
        repeat(OVERVIEW_LINE_COUNT) { index ->
            SkeletonPlate(
                Modifier
                    .fillMaxWidth(if (index == OVERVIEW_LINE_COUNT - 1) OVERVIEW_LAST_LINE_FRACTION else 1f)
                    .height(lineHeightOf(MaterialTheme.typography.bodyMedium)),
            )
        }
    }
}

/** [com.binge.designsystem.component.BingeTag]'s own height and corner, on a plausible fixed width — the real tag sizes to its label. */
@Composable
private fun ChipSkeleton(modifier: Modifier = Modifier) {
    SkeletonPlate(
        modifier
            .width(dimensionResource(R.dimen.request_skeleton_chip_width))
            .height(lineHeightOf(MaterialTheme.typography.labelSmallEmphasis) + dimensionResource(DesR.dimen.tag_padding_v) * 2),
        shape = BingeShapes.Tag,
    )
}

@Composable
private fun FactsSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        SkeletonPlate(Modifier.size(dimensionResource(DesR.dimen.detail_stat_icon_size)))
        // Weighted, not fillMaxWidth on the row: the value measures against what's left after the
        // fixed icon width, not the row's own full width.
        Box(modifier = Modifier.weight(1f)) {
            SkeletonPlate(
                Modifier
                    .fillMaxWidth(FACTS_VALUE_FRACTION)
                    .height(lineHeightOf(MaterialTheme.typography.bodyMedium)),
            )
        }
    }
}

@Composable
private fun PrimaryActionSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(top = dimensionResource(DesR.dimen.padding_sm), bottom = dimensionResource(DesR.dimen.padding_l)),
    ) {
        SkeletonPlate(
            Modifier.fillMaxWidth().height(dimensionResource(DesR.dimen.button_filled_height)),
            shape = BingeShapes.Medium,
        )
    }
}
