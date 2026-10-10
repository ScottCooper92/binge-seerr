package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.component.lineHeightOf
import com.binge.designsystem.navOverlayStart
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.labelSmallEmphasis
import com.binge.designsystem.R as DesR

/**
 * [com.binge.designsystem.component.SectionHeader]'s own band — its default padding
 * (`padding_m` + [navOverlayStart] on the start, `padding_m` on the end) and a title plate on its
 * `headlineSmall` floor height, so the content below doesn't jump on resolve. Shared because every detail skeleton
 * with a section header (`UserDetailSkeleton`, `IssueDetailSkeleton`, …) stands in front of the same component.
 */
@Composable
internal fun SectionHeaderSkeleton(modifier: Modifier = Modifier) {
    val horizontalPadding = dimensionResource(DesR.dimen.padding_m)
    Box(
        modifier =
            modifier
                .padding(
                    start = horizontalPadding + navOverlayStart(),
                    end = horizontalPadding,
                    top = dimensionResource(DesR.dimen.section_header_padding_v),
                    bottom = dimensionResource(DesR.dimen.section_header_padding_v),
                ).heightIn(min = dimensionResource(DesR.dimen.min_touch_target)),
        contentAlignment = Alignment.CenterStart,
    ) {
        SkeletonPlate(
            Modifier
                .width(
                    dimensionResource(DesR.dimen.info_row_label_min_width),
                ).height(lineHeightOf(MaterialTheme.typography.headlineSmall)),
        )
    }
}

/** [com.binge.designsystem.component.BingeTag]'s own height and corner, on a plausible fixed [width]: the real tag sizes to its label. */
@Composable
internal fun ChipSkeleton(
    width: Dp,
    modifier: Modifier = Modifier,
) {
    SkeletonPlate(
        modifier
            .width(width)
            .height(lineHeightOf(MaterialTheme.typography.labelSmallEmphasis) + dimensionResource(DesR.dimen.tag_padding_v) * 2),
        shape = BingeShapes.Tag,
    )
}
