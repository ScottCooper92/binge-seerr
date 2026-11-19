package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

private const val PORTRAIT_ASPECT = 2f / 3f
private const val SHORT_BAR_FRACTION = 0.6f

/**
 * What a person looks like while they load: the portrait's block and three bars for the name and facts. Back stays in the bar above.
 * It starts at [inset], the page's reading inset, where the profile starts once loaded.
 */
@Composable
internal fun PersonSkeleton(
    inset: Dp,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(DesR.dimen.padding_m)
    Row(
        modifier.padding(horizontal = inset).layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE)),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        SkeletonPlate(
            Modifier.width(dimensionResource(R.dimen.person_portrait_width)).aspectRatio(PORTRAIT_ASPECT),
            shape = BingeShapes.ProfilePoster,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            SkeletonPlate(Modifier.fillMaxWidth().height(dimensionResource(R.dimen.person_skeleton_name_height)))
            SkeletonPlate(Modifier.fillMaxWidth(SHORT_BAR_FRACTION).height(dimensionResource(DesR.dimen.skeleton_header_height)))
            SkeletonPlate(Modifier.fillMaxWidth(SHORT_BAR_FRACTION).height(dimensionResource(DesR.dimen.skeleton_header_height)))
        }
    }
}

/** The rails' place while the credits load, so the page does not jump when they arrive; it starts at the rails' [inset]. */
@Composable
internal fun CreditsSkeleton(
    inset: Dp,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(DesR.dimen.padding_m)
    Column(modifier.padding(horizontal = inset), verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        SkeletonPlate(
            Modifier
                .width(
                    dimensionResource(DesR.dimen.skeleton_header_width),
                ).height(dimensionResource(DesR.dimen.skeleton_header_height)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(CREDIT_PLACEHOLDERS) {
                SkeletonPlate(
                    Modifier.width(dimensionResource(DesR.dimen.card_width)).aspectRatio(PORTRAIT_ASPECT),
                    shape = BingeShapes.MediaCard,
                )
            }
        }
    }
}

private const val CREDIT_PLACEHOLDERS = 2
