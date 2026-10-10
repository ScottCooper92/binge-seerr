package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.component.lineHeightOf
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.state.ChipSkeleton
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.StatusChipSkeleton
import com.binge.designsystem.R as DesR

private const val HERO_TITLE_FRACTION = 0.7f
private const val OVERVIEW_LINE_COUNT = 3
private const val OVERVIEW_LAST_LINE_FRACTION = 0.6f

private const val CARD_TITLE_FRACTION = 0.5f
private const val CARD_ROW_TITLE_FRACTION = 0.6f
private const val CARD_ROW_DETAIL_FRACTION = 0.4f

/**
 * Loading placeholder for [RequestDetailPage]: the hero with its title and meta chips, the headline's overview, and
 * a [RequestCard] stand-in: the title row with its chip, one person row and the button. Stats, the
 * info rows, the destination line, "Other requests" and Moderated by are all conditional on what the
 * server returns, so nothing is reserved for them — the scroll grows on resolve rather than
 * reflowing a guess, the same trade Binge's own `DetailScreenSkeleton` makes for its cast rail (#373).
 *
 * There is no primary action footer to pin: the button lives inside the card, and the card
 * placeholder reserves it.
 *
 * Insets mirror [RequestDetailPage] exactly rather than a blanket `safeDrawingPadding()`: the hero
 * runs full-bleed under the status bar on both, and the scroll itself clears the navigation bar —
 * matching that is what keeps the [LayoutAnchors.Detail.HERO] anchor from moving on resolve.
 */
@Composable
internal fun RequestDetailSkeleton(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            HeroSkeleton(modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.HERO)))
            HeadlineSkeleton(
                modifier =
                    Modifier
                        .padding(resolvedContentPadding(vertical = resolvedContentInset()))
                        .layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW)),
            )
            RequestCardSkeleton(modifier = Modifier.padding(resolvedContentPadding()))
        }
    }
}

/** What sits under a seeded hero: the skeleton's headline and card, or the error and its retry once the refresh has failed. */
@Composable
internal fun RequestDetailSeededBody(
    error: SeerrError?,
    onRetry: () -> Unit,
) {
    if (error != null) {
        ErrorScreen(error = error, onRetry = onRetry)
    } else {
        HeadlineSkeleton(modifier = Modifier.padding(resolvedContentPadding(vertical = resolvedContentInset())))
        RequestCardSkeleton(modifier = Modifier.padding(resolvedContentPadding()))
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
            // The title and meta row only — RequestDetailPage passes no tagline/eyebrow, so those
            // rows never render and reserving them would leave a gap the resolved hero never fills.
            // The meta row is RequestHeroMeta's: a media type tag, then the state chip. The 4K tag is
            // conditional, so nothing is reserved for it.
            SkeletonPlate(
                Modifier
                    .fillMaxWidth(HERO_TITLE_FRACTION)
                    .height(lineHeightOf(MaterialTheme.typography.displaySmall)),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipSkeleton(dimensionResource(R.dimen.request_skeleton_chip_width))
                StatusChipSkeleton(dimensionResource(R.dimen.request_skeleton_chip_width))
            }
        }
    }
}

@Composable
private fun HeadlineSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        repeat(OVERVIEW_LINE_COUNT) { index ->
            SkeletonPlate(
                Modifier
                    .fillMaxWidth(if (index == OVERVIEW_LINE_COUNT - 1) OVERVIEW_LAST_LINE_FRACTION else 1f)
                    .height(lineHeightOf(MaterialTheme.typography.bodyMedium)),
            )
        }
    }
}

/**
 * [RequestCard]'s shape: the title row with a chip at its end, one person row and the button. The
 * button is reserved because the common viewer of an admin console has one; the rarer viewer without
 * it sees the card shrink on resolve, which beats the card growing.
 */
@Composable
private fun RequestCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().cardSurface()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(cardRowPadding()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SkeletonPlate(
                    Modifier
                        .fillMaxWidth(CARD_TITLE_FRACTION)
                        .height(lineHeightOf(MaterialTheme.typography.titleLarge)),
                )
            }
            StatusChipSkeleton(dimensionResource(R.dimen.request_skeleton_chip_width))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(cardRowPadding()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonPlate(Modifier.size(dimensionResource(DesR.dimen.item_group_icon_size)), shape = BingeShapes.MoreCard)
            Spacer(Modifier.width(dimensionResource(DesR.dimen.account_card_spacing)))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonPlate(
                    Modifier
                        .fillMaxWidth(CARD_ROW_TITLE_FRACTION)
                        .height(lineHeightOf(MaterialTheme.typography.titleMedium)),
                )
                SkeletonPlate(
                    Modifier
                        .fillMaxWidth(CARD_ROW_DETAIL_FRACTION)
                        .height(lineHeightOf(MaterialTheme.typography.bodyMedium)),
                )
            }
        }
        SkeletonPlate(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(dimensionResource(R.dimen.request_skeleton_button_width))
                .height(dimensionResource(DesR.dimen.button_filled_height)),
            shape = BingeShapes.Medium,
        )
    }
}
